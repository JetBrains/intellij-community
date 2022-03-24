// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.ide.util.EditorHelper;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveClassesOrPackagesCallback;
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;


public class MoveClassToInnerProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(MoveClassToInnerProcessor.class);
  public static final Key<List<NonCodeUsageInfo>> ourNonCodeUsageKey = Key.create("MoveClassToInner.NonCodeUsage");

  private PsiClass[] myClassesToMove;
  private final PsiClass myTargetClass;
  private PsiPackage[] mySourcePackage;
  private final PsiPackage myTargetPackage;
  private String[] mySourceVisibility;
  private final boolean mySearchInComments;
  private final boolean mySearchInNonJavaFiles;
  private NonCodeUsageInfo[] myNonCodeUsages;
  private final MoveCallback myMoveCallback;
  private boolean myOpenInEditor;

  public MoveClassToInnerProcessor(Project project,
                                   final PsiClass[] classesToMove,
                                   @NotNull final PsiClass targetClass,
                                   boolean searchInComments,
                                   boolean searchInNonJavaFiles,
                                   MoveCallback moveCallback) {
    super(project);
    setClassesToMove(classesToMove);
    myTargetClass = targetClass;
    mySearchInComments = searchInComments;
    mySearchInNonJavaFiles = searchInNonJavaFiles;
    myMoveCallback = moveCallback;
    myTargetPackage = JavaDirectoryService.getInstance().getPackage(myTargetClass.getContainingFile().getContainingDirectory());
}

  private void setClassesToMove(final PsiClass[] classesToMove) {
    myClassesToMove = classesToMove;
    mySourcePackage = new PsiPackage[classesToMove.length];
    mySourceVisibility = new String[classesToMove.length];
    for (int i = 0; i < classesToMove.length; i++) {
      PsiClass psiClass = classesToMove[i];
      mySourceVisibility[i] = VisibilityUtil.getVisibilityModifier(psiClass.getModifierList());
      mySourcePackage[i] = JavaDirectoryService.getInstance().getPackage(psiClass.getContainingFile().getContainingDirectory());
    }
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new MoveMultipleElementsViewDescriptor(myClassesToMove, myTargetClass.getQualifiedName());
  }

  @Override
  public UsageInfo @NotNull [] findUsages() {
    final List<UsageInfo> usages = new ArrayList<>();
    for (PsiClass classToMove : myClassesToMove) {
      final String newName = myTargetClass.getQualifiedName() + "." + classToMove.getName();
      Collections.addAll(usages, MoveClassesOrPackagesUtil.findUsages(
        classToMove, myRefactoringScope, mySearchInComments, mySearchInNonJavaFiles, newName));
    }
    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  @Override
  protected boolean preprocessUsages(@NotNull final Ref<UsageInfo[]> refUsages) {
    final UsageInfo[] usages = refUsages.get();
    return showConflicts(getConflicts(usages), usages);
  }

  @Override
  protected void refreshElements(final PsiElement @NotNull [] elements) {
    ApplicationManager.getApplication().runReadAction(() -> {
      final PsiClass[] classesToMove = new PsiClass[elements.length];
      for (int i = 0; i < classesToMove.length; i++) {
        classesToMove[i] = (PsiClass)elements[i];
      }
      setClassesToMove(classesToMove);
    });
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    MoveClassToInnerHandler[] handlers = MoveClassToInnerHandler.EP_NAME.getExtensions();

    ArrayList<UsageInfo> usageList = new ArrayList<>(Arrays.asList(usages));
    List<PsiElement> importStatements = new ArrayList<>();
    for (MoveClassToInnerHandler handler : handlers) {
      importStatements.addAll(handler.filterImports(usageList, myProject));
    }

    usages = usageList.toArray(UsageInfo.EMPTY_ARRAY);

    saveNonCodeUsages(usages);
    final Map<PsiElement, PsiElement> oldToNewElementsMapping = new HashMap<>();
    try {
      for (PsiClass classToMove : myClassesToMove) {
        PsiClass newClass = null;
        for (MoveClassToInnerHandler handler : handlers) {
          newClass = handler.moveClass(classToMove, myTargetClass);
          if (newClass != null) break;
        }
        LOG.assertTrue(newClass != null, "There is no appropriate MoveClassToInnerHandler for " + myTargetClass + "; " + classToMove);
        oldToNewElementsMapping.put(classToMove, newClass);
      }

      myNonCodeUsages = CommonMoveUtil.retargetUsages(usages, oldToNewElementsMapping);
      for (MoveClassToInnerHandler handler : handlers) {
        handler.retargetNonCodeUsages(oldToNewElementsMapping, myNonCodeUsages);
      }

      for (MoveClassToInnerHandler handler : handlers) {
        handler.retargetClassRefsInMoved(oldToNewElementsMapping);
      }

      for (MoveClassToInnerHandler handler : handlers) {
        handler.removeRedundantImports(myTargetClass.getContainingFile());
      }

      for (PsiClass classToMove : myClassesToMove) {
        classToMove.delete();
      }

      for (PsiElement element : importStatements) {
        if (element.isValid()) {
          element.delete();
        }
      }

      if (myOpenInEditor && !oldToNewElementsMapping.isEmpty()) {
        final PsiElement item = ContainerUtil.getFirstItem(oldToNewElementsMapping.values());
        if (item != null) {
          EditorHelper.openInEditor(item);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void saveNonCodeUsages(final UsageInfo[] usages) {
    for (PsiClass classToMove : myClassesToMove) {
      for(UsageInfo usageInfo: usages) {
        if (usageInfo instanceof NonCodeUsageInfo) {
          final NonCodeUsageInfo nonCodeUsage = (NonCodeUsageInfo)usageInfo;
          PsiElement element = nonCodeUsage.getElement();
          if (element != null && PsiTreeUtil.isAncestor(classToMove, element, false)) {
            List<NonCodeUsageInfo> list = element.getCopyableUserData(ourNonCodeUsageKey);
            if (list == null) {
              list = new ArrayList<>();
              element.putCopyableUserData(ourNonCodeUsageKey, list);
            }
            list.add(nonCodeUsage);
          }
        }
      }
    }
  }

  @Override
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

  @Override
  @NotNull
  protected String getCommandName() {
    return JavaRefactoringBundle.message("move.class.to.inner.command.name",
                                     myClassesToMove.length,
                                     StringUtil.join(myClassesToMove, psiClass -> psiClass.getName(), ", "),
                                     myTargetClass.getQualifiedName());
  }

  @Override
  @NotNull
  protected Collection<? extends PsiElement> getElementsToWrite(@NotNull final UsageViewDescriptor descriptor) {
    List<PsiElement> result = new ArrayList<>(super.getElementsToWrite(descriptor));
    result.add(myTargetClass);
    return result;
  }

  public MultiMap<PsiElement, String> getConflicts(final UsageInfo[] usages) {
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();

    for (PsiClass classToMove : myClassesToMove) {
      final PsiClass innerClass = myTargetClass.findInnerClassByName(classToMove.getName(), false);
      if (innerClass != null) {
        conflicts.putValue(innerClass, JavaRefactoringBundle.message("move.to.inner.duplicate.inner.class",
                                                CommonRefactoringUtil.htmlEmphasize(myTargetClass.getQualifiedName()),
                                                CommonRefactoringUtil.htmlEmphasize(classToMove.getName())));
      }
    }

    for (int i = 0; i <  myClassesToMove.length; i++) {
      PsiClass classToMove = myClassesToMove[i];
      String classToMoveVisibility = VisibilityUtil.getVisibilityModifier(classToMove.getModifierList());
      String targetClassVisibility = VisibilityUtil.getVisibilityModifier(myTargetClass.getModifierList());

      boolean moveToOtherPackage = !Comparing.equal(mySourcePackage[i], myTargetPackage);
      if (moveToOtherPackage) {
        classToMove.accept(new PackageLocalsUsageCollector(myClassesToMove, new PackageWrapper(myTargetPackage), conflicts));
      }

      ConflictsCollector collector = new ConflictsCollector(classToMove, conflicts);
      if ((moveToOtherPackage &&
           (classToMoveVisibility.equals(PsiModifier.PACKAGE_LOCAL) || targetClassVisibility.equals(PsiModifier.PACKAGE_LOCAL))) ||
          targetClassVisibility.equals(PsiModifier.PRIVATE)) {
        detectInaccessibleClassUsages(usages, collector, mySourceVisibility[i]);
      }
      if (moveToOtherPackage) {
        detectInaccessibleMemberUsages(collector);
      }
    }

    return conflicts;
  }

  private void detectInaccessibleClassUsages(final UsageInfo[] usages, final ConflictsCollector collector, final String visibility) {
    for(UsageInfo usage: usages) {
      if (usage instanceof MoveRenameUsageInfo && !(usage instanceof NonCodeUsageInfo)) {
        PsiElement element = usage.getElement();
        if (element == null || PsiTreeUtil.getParentOfType(element, PsiImportStatement.class) != null) continue;
        if (isInaccessibleFromTarget(element, visibility)) {
          collector.addConflict(collector.getClassToMove(), element);
        }
      }
    }
  }

  private boolean isInaccessibleFromTarget(final PsiElement element, final String visibility) {
    final PsiPackage elementPackage = JavaDirectoryService.getInstance().getPackage(element.getContainingFile().getContainingDirectory());
    return !PsiUtil.isAccessible(myTargetClass, element, null) ||
        (!myTargetClass.isInterface() && visibility.equals(PsiModifier.PACKAGE_LOCAL) && !Comparing.equal(elementPackage, myTargetPackage));
  }

  private void detectInaccessibleMemberUsages(final ConflictsCollector collector) {
    PsiElement[] members = collectPackageLocalMembers(collector.getClassToMove());
    for(PsiElement member: members) {
      ReferencesSearch.search(member).forEach(psiReference -> {
        PsiElement element = psiReference.getElement();
        for (PsiClass psiClass : myClassesToMove) {
          if (PsiTreeUtil.isAncestor(psiClass, element, false)) return true;
        }
        if (isInaccessibleFromTarget(element, PsiModifier.PACKAGE_LOCAL)) {
          collector.addConflict(psiReference.resolve(), element);
        }
        return true;
      });
    }
  }

  private static PsiElement[] collectPackageLocalMembers(PsiElement classToMove) {
    return PsiTreeUtil.collectElements(classToMove, element -> {
      if (element instanceof PsiMember) {
        PsiMember member = (PsiMember) element;
        if (VisibilityUtil.getVisibilityModifier(member.getModifierList()) == PsiModifier.PACKAGE_LOCAL) {
          return true;
        }
      }
      return false;
    });
  }

  public void setOpenInEditor(boolean openInEditor) {
    myOpenInEditor = openInEditor;
  }

  private static class ConflictsCollector {
    private final PsiClass myClassToMove;
    private final MultiMap<PsiElement, String> myConflicts;
    private final Set<PsiElement> myReportedContainers = new HashSet<>();

    ConflictsCollector(PsiClass classToMove, final MultiMap<PsiElement, String> conflicts) {
      myClassToMove = classToMove;
      myConflicts = conflicts;
    }

    public synchronized void addConflict(final PsiElement targetElement, final PsiElement sourceElement) {
      PsiElement container = ConflictsUtil.getContainer(sourceElement);
      if (!myReportedContainers.contains(container)) {
        myReportedContainers.add(container);
        String targetDescription = StringUtil.capitalize(RefactoringUIUtil.getDescription(targetElement, true));
        final String message = JavaRefactoringBundle.message("element.will.no.longer.be.accessible",
                                                         targetDescription,
                                                         RefactoringUIUtil.getDescription(container, true));
        myConflicts.putValue(targetElement, message);
      }
    }

    public PsiElement getClassToMove() {
      return myClassToMove;
    }
  }
}
