// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;


public class RenameJavaClassProcessor extends RenamePsiElementProcessor {
  private static final Logger LOG = Logger.getInstance(RenameJavaClassProcessor.class);

  @Override
  public boolean canProcessElement(@NotNull final PsiElement element) {
    return element instanceof PsiClass;
  }

  @Override
  public void renameElement(@NotNull final PsiElement element,
                            @NotNull final String newName,
                            final UsageInfo @NotNull [] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    PsiClass aClass = (PsiClass) element;
    ArrayList<UsageInfo> postponedCollisions = new ArrayList<>();
    List<MemberHidesOuterMemberUsageInfo> hidesOut = new ArrayList<>();
    // rename all references
    for (final UsageInfo usage : usages) {
      if (usage instanceof ResolvableCollisionUsageInfo) {
        if (usage instanceof CollidingClassImportUsageInfo) {
          ((CollidingClassImportUsageInfo)usage).getImportStatement().delete();
        } else if (usage instanceof MemberHidesOuterMemberUsageInfo) {
          final PsiElement usageElement = usage.getElement();
          final PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement)usageElement;
          if (collidingRef != null) {
            hidesOut.add(new MemberHidesOuterMemberUsageInfo(usageElement, (PsiClass)collidingRef.resolve()));
          }
        }
        else {
          postponedCollisions.add(usage);
        }
      }
    }

    // do actual rename
    PsiElement topLevelScope = aClass.getContainingClass() != null ? PsiTreeUtil.getTopmostParentOfType(aClass, PsiClass.class) //allow conflict resolution 
                                                                   : aClass.getContainingFile();
    ChangeContextUtil.encodeContextInfo(topLevelScope, true, false);
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
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }

    ChangeContextUtil.decodeContextInfo(aClass.getContainingFile(), null, null); //to make refs to other classes from this one resolve to their old referent

    // resolve collisions
    for (UsageInfo postponedCollision : postponedCollisions) {
      ClassHidesImportedClassUsageInfo collision = (ClassHidesImportedClassUsageInfo) postponedCollision;
      collision.resolveCollision();
    }

    for (MemberHidesOuterMemberUsageInfo usage : hidesOut) {
      PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement)usage.getElement();
      PsiMember member = (PsiMember)usage.getReferencedElement();
      if (collidingRef != null && collidingRef.isValid() && member != null && member.isValid()) {
        final PsiManager manager = member.getManager();
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(member.getProject());
        final String name = member.getName();
        final PsiClass containingClass = member.getContainingClass();
        if (name != null && containingClass != null) {
          if (manager.areElementsEquivalent(factory.createReferenceFromText(name, collidingRef).resolve(), member)) continue;
          final PsiJavaCodeReferenceElement ref = factory.createReferenceFromText("A." + name, collidingRef);
          final PsiJavaCodeReferenceElement qualifier = (PsiJavaCodeReferenceElement)ref.getQualifier();
          LOG.assertTrue(qualifier != null);
          final PsiJavaCodeReferenceElement classReference = factory.createClassReferenceElement(containingClass);
          qualifier.replace(classReference);
          collidingRef.replace(ref);
        }
      }
    }
    if (listener != null) {
      listener.elementRenamed(aClass);
    }
  }

  @Override
  @Nullable
  public Pair<String, String> getTextOccurrenceSearchStrings(@NotNull final PsiElement element, @NotNull final String newName) {
    if (element instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)element;
      if (aClass.getParent() instanceof PsiClass) {
        final String dollaredStringToSearch = ClassUtil.getJVMClassName(aClass);
        final String dollaredStringToReplace = dollaredStringToSearch == null ? null : RefactoringUtil.getNewInnerClassName(aClass, dollaredStringToSearch, newName);
        if (dollaredStringToReplace != null) {
          return Pair.create(dollaredStringToSearch, dollaredStringToReplace);
        }
      }
    }
    return null;
  }

  @Override
  public String getQualifiedNameAfterRename(@NotNull final PsiElement element, @NotNull final String newName, final boolean nonJava) {
    if (nonJava) {
      final PsiClass aClass = (PsiClass)element;
      return PsiUtilCore.getQualifiedNameAfterRename(aClass.getQualifiedName(), newName);
    }
    else {
      return newName;
    }
  }

  @Override
  public void prepareRenaming(@NotNull PsiElement element, @NotNull String newName, @NotNull Map<PsiElement, String> allRenames, @NotNull SearchScope scope) {
    final PsiMethod[] constructors = ((PsiClass) element).getConstructors();
    for (PsiMethod constructor : constructors) {
      if (constructor instanceof PsiMirrorElement) {
        final PsiElement prototype = ((PsiMirrorElement)constructor).getPrototype();
        if (prototype instanceof PsiNamedElement) {
          allRenames.put(prototype, newName);
        }
      }
      else if (constructor instanceof LightRecordCanonicalConstructor) {
        allRenames.put(constructor, newName);
      }
      else if (!(constructor instanceof LightElement)) {
        allRenames.put(constructor, newName);
      }
    }
  }

  @Override
  public void findCollisions(@NotNull final PsiElement element, @NotNull final String newName, @NotNull final Map<? extends PsiElement, String> allRenames, @NotNull final List<UsageInfo> result) {
    final PsiClass aClass = (PsiClass)element;
    final ClassCollisionsDetector classCollisionsDetector = new ClassCollisionsDetector(aClass);
    Collection<UsageInfo> initialResults = new ArrayList<>(result);
    for(UsageInfo usageInfo: initialResults) {
      if (usageInfo instanceof MoveRenameUsageInfo) {
        classCollisionsDetector.addClassCollisions(usageInfo.getElement(), newName, result);
      }
    }
    findSubmemberHidesMemberCollisions(aClass, newName, result);

    if (aClass instanceof PsiTypeParameter) {
      final PsiTypeParameterListOwner owner = ((PsiTypeParameter)aClass).getOwner();
      if (owner != null) {
        for (PsiTypeParameter typeParameter : owner.getTypeParameters()) {
          if (Objects.equals(newName, typeParameter.getName())) {
            result.add(new UnresolvableCollisionUsageInfo(aClass, typeParameter) {
              @Override
              public String getDescription() {
                return JavaRefactoringBundle
                  .message("there.is.already.type.parameter.in.0.with.name.1", RefactoringUIUtil.getDescription(aClass, false), newName);
              }
            });
          }
        }
      }
    }
  }

  public static void findSubmemberHidesMemberCollisions(final PsiClass aClass, final String newName, final List<UsageInfo> result) {
    if (aClass.getParent() instanceof PsiClass) {
      PsiClass parent = (PsiClass)aClass.getParent();
      Collection<PsiClass> inheritors = ClassInheritorsSearch.search(parent).findAll();
      for (PsiClass inheritor : inheritors) {
        if (newName.equals(inheritor.getName())) {
          final ClassCollisionsDetector classCollisionsDetector = new ClassCollisionsDetector(aClass);
          for (PsiReference reference : ReferencesSearch.search(inheritor, new LocalSearchScope(inheritor))) {
            classCollisionsDetector.addClassCollisions(reference.getElement(), newName, result);
          }
        }
        PsiClass[] inners = inheritor.getInnerClasses();
        for (PsiClass inner : inners) {
          if (newName.equals(inner.getName())) {
            result.add(new SubmemberHidesMemberUsageInfo(inner, aClass));
          }
        }
      }
    } else if (aClass instanceof PsiTypeParameter) {
      final PsiTypeParameterListOwner owner = ((PsiTypeParameter)aClass).getOwner();
      if (owner instanceof PsiClass) {
        final PsiClass[] supers = ((PsiClass)owner).getSupers();
        for (PsiClass superClass : supers) {
          if (newName.equals(superClass.getName())) {
            final ClassCollisionsDetector classCollisionsDetector = new ClassCollisionsDetector(aClass);
            for (PsiReference reference : ReferencesSearch.search(superClass, new LocalSearchScope(superClass))) {
              classCollisionsDetector.addClassCollisions(reference.getElement(), newName, result);
            }
          }
          PsiClass[] inners = superClass.getInnerClasses();
          for (final PsiClass inner : inners) {
            if (newName.equals(inner.getName())) {
              ReferencesSearch.search(inner).forEach(reference -> {
                PsiElement refElement = reference.getElement();
                if (refElement instanceof PsiReferenceExpression && ((PsiReferenceExpression)refElement).isQualified()) return true;
                MemberHidesOuterMemberUsageInfo info = new MemberHidesOuterMemberUsageInfo(refElement, aClass);
                result.add(info);
                return true;
              });
            }
          }
        }
      }
    }
  }

  private static class ClassCollisionsDetector {
    final HashSet<PsiFile> myProcessedFiles = new HashSet<>();
    final PsiClass myRenamedClass;
    private final String myRenamedClassQualifiedName;

    ClassCollisionsDetector(PsiClass renamedClass) {
      myRenamedClass = renamedClass;
      myRenamedClassQualifiedName = myRenamedClass.getQualifiedName();
    }

    public void addClassCollisions(PsiElement referenceElement, String newName, List<UsageInfo> results) {
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(referenceElement.getProject()).getResolveHelper();
      final PsiClass aClass = resolveHelper.resolveReferencedClass(newName, referenceElement);
      if (aClass == null) return;
      if (aClass instanceof PsiTypeParameter && myRenamedClass instanceof PsiTypeParameter) {
        final PsiTypeParameterListOwner member = PsiTreeUtil.getParentOfType(referenceElement, PsiTypeParameterListOwner.class);
        if (member != null) {
          final PsiTypeParameterList typeParameterList = member.getTypeParameterList();
          if (typeParameterList != null && ArrayUtilRt.find(typeParameterList.getTypeParameters(), myRenamedClass) > -1) {
            if (member.hasModifierProperty(PsiModifier.STATIC)) return;
          }
        }
      }
      final PsiFile containingFile = referenceElement.getContainingFile();
      final String text = referenceElement.getText();
      if (Objects.equals(myRenamedClassQualifiedName, removeSpaces(text))) return;
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

  @Override
  public void findExistingNameConflicts(@NotNull final PsiElement element, @NotNull final String newName, @NotNull final MultiMap<PsiElement,String> conflicts) {
    if (element instanceof PsiCompiledElement) return;
    final PsiClass aClass = (PsiClass)element;
    if (newName.equals(aClass.getName())) return;
    final PsiClass containingClass = aClass.getContainingClass();
    if (containingClass != null) { // innerClass
      PsiClass[] innerClasses = containingClass.getInnerClasses();
      for (PsiClass innerClass : innerClasses) {
        if (newName.equals(innerClass.getName())) {
          conflicts.putValue(innerClass, JavaRefactoringBundle.message("inner.class.0.is.already.defined.in.class.1", newName, containingClass.getQualifiedName()));
          break;
        }
      }
    }
    else if (!(aClass instanceof PsiTypeParameter)) {
      final String qualifiedNameAfterRename = PsiUtilCore.getQualifiedNameAfterRename(aClass.getQualifiedName(), newName);
      Project project = element.getProject();
      final PsiClass conflictingClass =
        JavaPsiFacade.getInstance(project).findClass(qualifiedNameAfterRename, GlobalSearchScope.allScope(project));
      if (conflictingClass != null) {
        conflicts.putValue(conflictingClass, JavaRefactoringBundle.message("class.0.already.exists", qualifiedNameAfterRename));
      }
    }
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpID(final PsiElement element) {
    return HelpID.RENAME_CLASS;
  }

  @Override
  public boolean isToSearchInComments(@NotNull final PsiElement psiElement) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_CLASS;
  }

  @Override
  public void setToSearchInComments(@NotNull final PsiElement element, final boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = enabled;
  }

  @Override
  public boolean isToSearchForTextOccurrences(@NotNull final PsiElement element) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_CLASS;
  }

  @Override
  public void setToSearchForTextOccurrences(@NotNull final PsiElement element, final boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_CLASS = enabled;
  }
}
