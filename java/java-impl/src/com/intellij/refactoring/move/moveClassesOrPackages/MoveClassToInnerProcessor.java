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
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveClassesOrPackagesCallback;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author yole
 */
public class MoveClassToInnerProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassToInnerProcessor");

  private PsiClass myClassToMove;
  private final PsiClass myTargetClass;
  private PsiPackage mySourcePackage;
  private final PsiPackage myTargetPackage;
  private String mySourceVisibility;
  private final boolean mySearchInComments;
  private final boolean mySearchInNonJavaFiles;
  private NonCodeUsageInfo[] myNonCodeUsages;
  private static final Key<List<NonCodeUsageInfo>> ourNonCodeUsageKey = Key.create("MoveClassToInner.NonCodeUsage");
  private final MoveCallback myMoveCallback;

  public MoveClassToInnerProcessor(Project project,
                                   final PsiClass classToMove,
                                   @NotNull final PsiClass targetClass,
                                   boolean searchInComments,
                                   boolean searchInNonJavaFiles,
                                   MoveCallback moveCallback) {
    super(project);
    setClassToMove(classToMove);
    myTargetClass = targetClass;
    mySearchInComments = searchInComments;
    mySearchInNonJavaFiles = searchInNonJavaFiles;
    myMoveCallback = moveCallback;
    myTargetPackage = JavaDirectoryService.getInstance().getPackage(myTargetClass.getContainingFile().getContainingDirectory());
}

  private void setClassToMove(final PsiClass classToMove) {
    myClassToMove = classToMove;
    mySourceVisibility = VisibilityUtil.getVisibilityModifier(myClassToMove.getModifierList());
    mySourcePackage = JavaDirectoryService.getInstance().getPackage(myClassToMove.getContainingFile().getContainingDirectory());
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new MoveClassesOrPackagesViewDescriptor(new PsiElement[] { myClassToMove },
                                                   mySearchInComments, mySearchInNonJavaFiles,
                                                   myTargetClass.getQualifiedName());
  }

  @NotNull
  public UsageInfo[] findUsages() {
    List<UsageInfo> usages = new ArrayList<UsageInfo>();
    String newName = myTargetClass.getQualifiedName() + "." + myClassToMove.getName();
    Collections.addAll(usages, MoveClassesOrPackagesUtil.findUsages(myClassToMove, mySearchInComments,
                                                                    mySearchInNonJavaFiles, newName));
    for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext();) {
      UsageInfo usageInfo = iterator.next();
      if (!(usageInfo instanceof NonCodeUsageInfo) && PsiTreeUtil.isAncestor(myClassToMove, usageInfo.getElement(), false)) {
        iterator.remove();
      }
    }
    return usages.toArray(new UsageInfo[usages.size()]);
  }

  protected boolean preprocessUsages(final Ref<UsageInfo[]> refUsages) {
    return showConflicts(getConflicts(refUsages.get()));
  }

  protected void refreshElements(final PsiElement[] elements) {
    assert elements.length == 1;
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        setClassToMove((PsiClass)elements[0]);
      }
    });
  }

  protected void performRefactoring(UsageInfo[] usages) {
    if (!prepareWritable(usages)) return;

    try {
      saveNonCodeUsages(usages);
      ChangeContextUtil.encodeContextInfo(myClassToMove, true);
      PsiClass newClass = (PsiClass)myTargetClass.addBefore(myClassToMove, myTargetClass.getRBrace());
      PsiUtil.setModifierProperty(newClass, PsiModifier.STATIC, true);
      newClass = (PsiClass)ChangeContextUtil.decodeContextInfo(newClass, null, null);

      retargetClassRefs(myClassToMove, newClass);

      final List<PsiElement> importStatements = new ArrayList<PsiElement>();
      if (!CodeStyleSettingsManager.getSettings(myProject).INSERT_INNER_CLASS_IMPORTS) {
        usages = filterUsagesInImportStatements(usages, importStatements);
      }

      Map<PsiElement, PsiElement> oldToNewElementsMapping = new HashMap<PsiElement, PsiElement>();
      oldToNewElementsMapping.put(myClassToMove, newClass);
      myNonCodeUsages = MoveClassesOrPackagesProcessor.retargetUsages(usages, oldToNewElementsMapping);
      retargetNonCodeUsages(newClass);

      JavaCodeStyleManager.getInstance(myProject).removeRedundantImports((PsiJavaFile)newClass.getContainingFile());

      myClassToMove.delete();
      for(PsiElement element: importStatements) {
        if (element.isValid()) {
          element.delete();
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private boolean prepareWritable(final UsageInfo[] usages) {
    Set<PsiElement> elementsToMakeWritable = new HashSet<PsiElement>();
    elementsToMakeWritable.add(myClassToMove);
    elementsToMakeWritable.add(myTargetClass);
    for(UsageInfo usage: usages) {
      PsiElement element = usage.getElement();
      if (element != null) {
        elementsToMakeWritable.add(element);
      }
    }
    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, elementsToMakeWritable.toArray(new PsiElement[elementsToMakeWritable.size()]))) {
      return false;
    }
    return true;
  }

  private void saveNonCodeUsages(final UsageInfo[] usages) {
    for(UsageInfo usageInfo: usages) {
      if (usageInfo instanceof NonCodeUsageInfo) {
        final NonCodeUsageInfo nonCodeUsage = (NonCodeUsageInfo)usageInfo;
        PsiElement element = nonCodeUsage.getElement();
        if (element != null && PsiTreeUtil.isAncestor(myClassToMove, element, false)) {
          List<NonCodeUsageInfo> list = element.getCopyableUserData(ourNonCodeUsageKey);
          if (list == null) {
            list = new ArrayList<NonCodeUsageInfo>();
            element.putCopyableUserData(ourNonCodeUsageKey, list);
          }
          list.add(nonCodeUsage);
        }
      }
    }
  }

  private void retargetNonCodeUsages(final PsiClass newClass) {
    newClass.accept(new PsiRecursiveElementVisitor() {
      @Override public void visitElement(final PsiElement element) {
        super.visitElement(element);
        List<NonCodeUsageInfo> list = element.getCopyableUserData(ourNonCodeUsageKey);
        if (list != null) {
          for(NonCodeUsageInfo info: list) {
            for(int i=0; i<myNonCodeUsages.length; i++) {
              if (myNonCodeUsages [i] == info) {
                myNonCodeUsages [i] = info.replaceElement(element);
                break;
              }
            }
          }
          element.putCopyableUserData(ourNonCodeUsageKey, null);
        }
      }
    });
  }

  protected void performPsiSpoilingRefactoring() {
    if (myNonCodeUsages != null) {
      RenameUtil.renameNonCodeUsages(myProject, myNonCodeUsages);
    }
    if (myMoveCallback != null) {
      if (myMoveCallback instanceof MoveClassesOrPackagesCallback) {
        ((MoveClassesOrPackagesCallback) myMoveCallback).classesMovedToInner(myTargetClass);
      }
      myMoveCallback.refactoringCompleted();
    }
  }

  private static void retargetClassRefs(final PsiClass classToMove, final PsiClass newClass) {
    newClass.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitReferenceElement(final PsiJavaCodeReferenceElement reference) {
        PsiElement element = reference.resolve();
        if (element instanceof PsiClass && PsiTreeUtil.isAncestor(classToMove, element, false)) {
          PsiClass newInnerClass = findMatchingClass(classToMove, newClass, (PsiClass) element);
          try {
            reference.bindToElement(newInnerClass);
          }
          catch(IncorrectOperationException ex) {
            LOG.error(ex);
          }
        }
        else {
          super.visitReferenceElement(reference);
        }
      }
    });
  }

  private static PsiClass findMatchingClass(final PsiClass classToMove, final PsiClass newClass, final PsiClass innerClass) {
    if (classToMove == innerClass) {
      return newClass;
    }
    PsiClass parentClass = findMatchingClass(classToMove, newClass, innerClass.getContainingClass());
    PsiClass newInnerClass = parentClass.findInnerClassByName(innerClass.getName(), false);
    assert newInnerClass != null;
    return newInnerClass;
  }

  private static UsageInfo[] filterUsagesInImportStatements(final UsageInfo[] usages, final List<PsiElement> importStatements) {
    List<UsageInfo> remainingUsages = new ArrayList<UsageInfo>();
    for(UsageInfo usage: usages) {
      PsiElement element = usage.getElement();
      if (element == null) continue;
      PsiImportStatement stmt = PsiTreeUtil.getParentOfType(element, PsiImportStatement.class);
      if (stmt != null) {
        importStatements.add(stmt);
      }
      else {
        remainingUsages.add(usage);
      }
    }
    return remainingUsages.toArray(new UsageInfo[remainingUsages.size()]);
  }

  protected String getCommandName() {
    return RefactoringBundle.message("move.class.to.inner.command.name",
                                     myClassToMove.getQualifiedName(),
                                     myTargetClass.getQualifiedName());
  }

  @NotNull
  protected Collection<? extends PsiElement> getElementsToWrite(@NotNull final UsageViewDescriptor descriptor) {
    List<PsiElement> result = new ArrayList<PsiElement>();
    result.addAll(super.getElementsToWrite(descriptor));
    result.add(myTargetClass);
    return result;
  }

  public MultiMap<PsiElement, String> getConflicts(final UsageInfo[] usages) {
    MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();

    final PsiClass innerClass = myTargetClass.findInnerClassByName(myClassToMove.getName(), false);
    if (innerClass != null) {
      conflicts.putValue(innerClass, RefactoringBundle.message("move.to.inner.duplicate.inner.class",
                                              CommonRefactoringUtil.htmlEmphasize(myTargetClass.getQualifiedName()),
                                              CommonRefactoringUtil.htmlEmphasize(myClassToMove.getName())));
    }

    String classToMoveVisibility =  VisibilityUtil.getVisibilityModifier(myClassToMove.getModifierList());
    String targetClassVisibility =  VisibilityUtil.getVisibilityModifier(myTargetClass.getModifierList());

    boolean moveToOtherPackage = !Comparing.equal(mySourcePackage, myTargetPackage);
    if (moveToOtherPackage) {
      PsiElement[] elementsToMove = new PsiElement[] { myClassToMove };
      myClassToMove.accept(new PackageLocalsUsageCollector(elementsToMove, new PackageWrapper(myTargetPackage), conflicts));
    }

    ConflictsCollector collector = new ConflictsCollector(conflicts);
    if ((moveToOtherPackage &&
         (classToMoveVisibility.equals(PsiModifier.PACKAGE_LOCAL) || targetClassVisibility.equals(PsiModifier.PACKAGE_LOCAL))) ||
        targetClassVisibility.equals(PsiModifier.PRIVATE)) {
      detectInaccessibleClassUsages(usages, collector);
    }
    if (moveToOtherPackage) {
      detectInaccessibleMemberUsages(collector);
    }

    return conflicts;
  }

  private void detectInaccessibleClassUsages(final UsageInfo[] usages, final ConflictsCollector collector) {
    for(UsageInfo usage: usages) {
      if (usage instanceof MoveRenameUsageInfo && !(usage instanceof NonCodeUsageInfo)) {
        PsiElement element = usage.getElement();
        if (element == null || PsiTreeUtil.getParentOfType(element, PsiImportStatement.class) != null) continue;
        if (isInaccessibleFromTarget(element, mySourceVisibility)) {
          collector.addConflict(myClassToMove, element);
        }
      }
    }
  }

  private boolean isInaccessibleFromTarget(final PsiElement element, final String visibility) {
    final PsiPackage elementPackage = JavaDirectoryService.getInstance().getPackage(element.getContainingFile().getContainingDirectory());
    return !PsiUtil.isAccessible(myTargetClass, element, null) ||
        (visibility.equals(PsiModifier.PACKAGE_LOCAL) && !Comparing.equal(elementPackage, myTargetPackage));
  }

  private void detectInaccessibleMemberUsages(final ConflictsCollector collector) {
    PsiElement[] members = collectPackageLocalMembers();
    for(PsiElement member: members) {
      ReferencesSearch.search(member).forEach(new Processor<PsiReference>() {
        public boolean process(final PsiReference psiReference) {
          PsiElement element = psiReference.getElement();
          if (isInaccessibleFromTarget(element, PsiModifier.PACKAGE_LOCAL)) {
            collector.addConflict(psiReference.resolve(), element);
          }
          return true;
        }
      });
    }
  }

  private PsiElement[] collectPackageLocalMembers() {
    return PsiTreeUtil.collectElements(myClassToMove, new PsiElementFilter() {
      public boolean isAccepted(final PsiElement element) {
        if (element instanceof PsiMember) {
          PsiMember member = (PsiMember) element;
          if (VisibilityUtil.getVisibilityModifier(member.getModifierList()) == PsiModifier.PACKAGE_LOCAL) {
            return true;
          }
        }
        return false;
      }
    });
  }

  private class ConflictsCollector {
    private final MultiMap<PsiElement, String> myConflicts;
    private final Set<PsiElement> myReportedContainers = new HashSet<PsiElement>();

    public ConflictsCollector(final MultiMap<PsiElement, String> conflicts) {
      myConflicts = conflicts;
    }

    public void addConflict(final PsiElement targetElement, final PsiElement sourceElement) {
      PsiElement container = ConflictsUtil.getContainer(sourceElement);
      if (!myReportedContainers.contains(container)) {
        myReportedContainers.add(container);
        String targetDescription = (targetElement == myClassToMove)
                                   ? "Class " + CommonRefactoringUtil.htmlEmphasize(myClassToMove.getName())
                                   : StringUtil.capitalize(RefactoringUIUtil.getDescription(targetElement, true));
        final String message = RefactoringBundle.message("element.will.no.longer.be.accessible",
                                                         targetDescription,
                                                         RefactoringUIUtil.getDescription(container, true));
        myConflicts.putValue(targetElement, message);
      }
    }
  }
}
