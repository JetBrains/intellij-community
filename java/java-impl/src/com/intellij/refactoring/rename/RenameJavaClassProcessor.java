/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.rename;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class RenameJavaClassProcessor extends RenamePsiElementProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameJavaClassProcessor");

  public boolean canProcessElement(final PsiElement element) {
    return element instanceof PsiClass;
  }

  public void renameElement(final PsiElement element,
                            final String newName,
                            final UsageInfo[] usages, final RefactoringElementListener listener) throws IncorrectOperationException {
    PsiClass aClass = (PsiClass) element;
    ArrayList<UsageInfo> postponedCollisions = new ArrayList<UsageInfo>();
    // rename all references
    for (final UsageInfo usage : usages) {
      if (usage instanceof ResolvableCollisionUsageInfo) {
        if (usage instanceof CollidingClassImportUsageInfo) {
          ((CollidingClassImportUsageInfo)usage).getImportStatement().delete();
        }
        else {
          postponedCollisions.add(usage);
        }
      }
    }

    // do actual rename
    ChangeContextUtil.encodeContextInfo(aClass, true);
    aClass.setName(newName);

    for (UsageInfo usage : usages) {
      if (!(usage instanceof ResolvableCollisionUsageInfo)) {
        final PsiReference ref = usage.getReference();
        if (ref == null) continue;
        try {
          ref.bindToElement(aClass);
        }
        catch (IncorrectOperationException e) {//fall back to old scheme
          ref.handleElementRename(newName);
        }
      }
    }

    ChangeContextUtil.decodeContextInfo(aClass, null, null); //to make refs to other classes from this one resolve to their old referent

    // resolve collisions
    for (UsageInfo postponedCollision : postponedCollisions) {
      ClassHidesImportedClassUsageInfo collision = (ClassHidesImportedClassUsageInfo) postponedCollision;
      collision.resolveCollision();
    }

    listener.elementRenamed(aClass);
  }

  @Nullable
  public Pair<String, String> getTextOccurrenceSearchStrings(@NotNull final PsiElement element, @NotNull final String newName) {
    if (element instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)element;
      if (aClass.getParent() instanceof PsiClass) {
        final String dollaredStringToSearch = ClassUtil.getJVMClassName(aClass);
        final String dollaredStringToReplace = dollaredStringToSearch == null ? null : RefactoringUtil.getNewInnerClassName(aClass, dollaredStringToSearch, newName);
        if (dollaredStringToReplace != null) {
          return new Pair<String, String>(dollaredStringToSearch, dollaredStringToReplace);
        }
      }
    }
    return null;
  }

  public String getQualifiedNameAfterRename(final PsiElement element, final String newName, final boolean nonJava) {
    if (nonJava) {
      final PsiClass aClass = (PsiClass)element;
      return RenameUtil.getQualifiedNameAfterRename(aClass.getQualifiedName(), newName);
    }
    else {
      return newName;
    }
  }

  public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
    final PsiMethod[] constructors = ((PsiClass) element).getConstructors();
    for (PsiMethod constructor : constructors) {
      allRenames.put(constructor, newName);
    }
  }

  public void findCollisions(final PsiElement element, final String newName, final Map<? extends PsiElement, String> allRenames, final List<UsageInfo> result) {
    final PsiClass aClass = (PsiClass)element;
    final ClassCollisionsDetector classCollisionsDetector = new ClassCollisionsDetector(aClass);
    Collection<UsageInfo> initialResults = new ArrayList<UsageInfo>(result);
    for(UsageInfo usageInfo: initialResults) {
      if (usageInfo instanceof MoveRenameUsageInfo) {
        classCollisionsDetector.addClassCollisions(usageInfo.getElement(), newName, result);
      }
    }
    findSubmemberHidesMemberCollisions(aClass, newName, result);
  }

  public static void findSubmemberHidesMemberCollisions(final PsiClass aClass, final String newName, final List<UsageInfo> result) {
    if (aClass.getParent() instanceof PsiClass) {
      PsiClass parent = (PsiClass)aClass.getParent();
      Collection<PsiClass> inheritors = ClassInheritorsSearch.search(parent, parent.getUseScope(), true).findAll();
      for (PsiClass inheritor : inheritors) {
        PsiClass[] inners = inheritor.getInnerClasses();
        for (PsiClass inner : inners) {
          if (newName.equals(inner.getName())) {
            result.add(new SubmemberHidesMemberUsageInfo(inner, aClass));
          }
        }
      }
    }
  }

  private static class ClassCollisionsDetector {
    final HashSet<PsiFile> myProcessedFiles = new HashSet<PsiFile>();
    final PsiClass myRenamedClass;
    private final String myRenamedClassQualifiedName;

    public ClassCollisionsDetector(PsiClass renamedClass) {
      myRenamedClass = renamedClass;
      myRenamedClassQualifiedName = myRenamedClass.getQualifiedName();
    }

    public void addClassCollisions(PsiElement referenceElement, String newName, List<UsageInfo> results) {
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(referenceElement.getProject()).getResolveHelper();
      final PsiClass aClass = resolveHelper.resolveReferencedClass(newName, referenceElement);
      if (aClass == null) return;
      final PsiFile containingFile = referenceElement.getContainingFile();
      final String text = referenceElement.getText();
      if (Comparing.equal(myRenamedClassQualifiedName, removeSpaces(text))) return;
      if (myProcessedFiles.contains(containingFile)) return;
      for (PsiReference reference : ReferencesSearch.search(aClass, new LocalSearchScope(containingFile))) {
        final PsiElement collisionReferenceElement = reference.getElement();
        if (collisionReferenceElement instanceof PsiJavaCodeReferenceElement) {
          final PsiElement parent = collisionReferenceElement.getParent();
          if (parent instanceof PsiImportStatement) {
            results.add(new CollidingClassImportUsageInfo((PsiImportStatement)parent, myRenamedClass));
          }
          else {
            if (aClass.getQualifiedName() != null) {
              results.add(new ClassHidesImportedClassUsageInfo((PsiJavaCodeReferenceElement)collisionReferenceElement,
                                                               myRenamedClass, aClass));
            }
            else {
              results.add(new ClassHidesUnqualifiableClassUsageInfo((PsiJavaCodeReferenceElement)collisionReferenceElement,
                                                                    myRenamedClass, aClass));
            }
          }
        }
      }
      myProcessedFiles.add(containingFile);
    }
  }

  @NonNls private static final Pattern WHITE_SPACE_PATTERN = Pattern.compile("\\s");

  private static String removeSpaces(String s) {
    return WHITE_SPACE_PATTERN.matcher(s).replaceAll("");
  }

  public void findExistingNameConflicts(final PsiElement element, final String newName, final MultiMap<PsiElement,String> conflicts) {
    if (element instanceof PsiCompiledElement) return;
    final PsiClass aClass = (PsiClass)element;
    if (newName.equals(aClass.getName())) return;
    final PsiClass containingClass = aClass.getContainingClass();
    if (containingClass != null) { // innerClass
      PsiClass[] innerClasses = containingClass.getInnerClasses();
      for (PsiClass innerClass : innerClasses) {
        if (newName.equals(innerClass.getName())) {
          conflicts.putValue(innerClass, RefactoringBundle.message("inner.class.0.is.already.defined.in.class.1", newName, containingClass.getQualifiedName()));
          break;
        }
      }
    }
    else if (!(aClass instanceof PsiTypeParameter)) {
      final String qualifiedNameAfterRename = RenameUtil.getQualifiedNameAfterRename(aClass.getQualifiedName(), newName);
      Project project = element.getProject();
      final PsiClass conflictingClass =
        JavaPsiFacade.getInstance(project).findClass(qualifiedNameAfterRename, GlobalSearchScope.allScope(project));
      if (conflictingClass != null) {
        conflicts.putValue(conflictingClass, RefactoringBundle.message("class.0.already.exists", qualifiedNameAfterRename));
      }
    }
  }

  @Nullable
  @NonNls
  public String getHelpID(final PsiElement element) {
    return HelpID.RENAME_CLASS;
  }

  public boolean isToSearchInComments(final PsiElement psiElement) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_CLASS;
  }

  public void setToSearchInComments(final PsiElement element, final boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = enabled;
  }

  public boolean isToSearchForTextOccurrences(final PsiElement element) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_CLASS;
  }

  public void setToSearchForTextOccurrences(final PsiElement element, final boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_CLASS = enabled;
  }
}
