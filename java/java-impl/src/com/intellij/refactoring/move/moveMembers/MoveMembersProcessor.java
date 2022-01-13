// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.move.moveMembers;

import com.intellij.ide.util.EditorHelper;
import com.intellij.model.ModelBranch;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveMemberViewDescriptor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RefactoringConflictsUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * created at Sep 11, 2001
 * @author Jeka
 */
public class MoveMembersProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(MoveMembersProcessor.class);

  private PsiClass myTargetClass;
  private final Set<PsiMember> myMembersToMove = new LinkedHashSet<>();
  private final MoveCallback myMoveCallback;
  private final boolean myOpenInEditor;
  private String myNewVisibility; // "null" means "as is"
  private @NlsContexts.Label String myCommandName = MoveMembersImpl.getRefactoringName();
  private MoveMembersOptions myOptions;

  public MoveMembersProcessor(Project project, MoveMembersOptions options) {
    this(project, null, options);
  }

  public MoveMembersProcessor(Project project, @Nullable MoveCallback moveCallback, MoveMembersOptions options) {
    this(project, moveCallback, options, false);
  }

  public MoveMembersProcessor(Project project, @Nullable MoveCallback moveCallback, MoveMembersOptions options, boolean openInEditor) {
    super(project);
    myMoveCallback = moveCallback;
    myOpenInEditor = openInEditor;
    setOptions(options);
  }

  @Override
  @NotNull
  @NlsContexts.Label
  protected String getCommandName() {
    return myCommandName;
  }

  @Override
  protected @Nullable String getRefactoringId() {
    return "refactoring.move.members";
  }

  @Override
  protected @Nullable RefactoringEventData getBeforeData() {
    PsiMember[] selectedMembers = myOptions.getSelectedMembers();

    RefactoringEventData data = new RefactoringEventData();
    data.addElement(selectedMembers[0].getContainingClass());
    data.addElements(selectedMembers);
    return data;
  }

  @Override
  protected @Nullable RefactoringEventData getAfterData(UsageInfo @NotNull [] usages) {
    RefactoringEventData eventData = new RefactoringEventData();
    eventData.addElement(myTargetClass);
    return eventData;
  }

  private void setOptions(MoveMembersOptions dialog) {
    myOptions = dialog;

    PsiMember[] members = dialog.getSelectedMembers();
    myMembersToMove.clear();
    ContainerUtil.addAll(myMembersToMove, members);

    setCommandName(members);

    final String targetClassName = dialog.getTargetClassName();
    myTargetClass = JavaPsiFacade.getInstance(myProject).findClass(targetClassName, GlobalSearchScope.projectScope(myProject));
    LOG.assertTrue(myTargetClass != null, "target class: " + targetClassName);
    myNewVisibility = dialog.getMemberVisibility();
  }

  private void setCommandName(final PsiMember[] members) {
    myCommandName = RefactoringBundle.message("move.0.title", StringUtil.join(members, member -> UsageViewUtil.getType(member) + " " + UsageViewUtil.getShortName(member), ", "));
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new MoveMemberViewDescriptor(PsiUtilCore.toPsiElementArray(myMembersToMove));
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    final List<UsageInfo> usagesList = new ArrayList<>();
    for (PsiMember member : myMembersToMove) {
      for (PsiReference psiReference : ReferencesSearch.search(member)) {
        PsiElement ref = psiReference.getElement();
        final MoveMemberHandler handler = MoveMemberHandler.EP_NAME.forLanguage(ref.getLanguage());
        MoveMembersUsageInfo usage = null;
        if (handler != null && myTargetClass != null) {
          usage = handler.getUsage(member, psiReference, myMembersToMove, myTargetClass);
        }
        if (usage != null) {
          usagesList.add(usage);
        }
        else {
          if (!isInMovedElement(ref)) {
            usagesList.add(new MoveMembersUsageInfo(member, ref, null, ref, psiReference));
          }
        }
      }
    }
    UsageInfo[] usageInfos = usagesList.toArray(UsageInfo.EMPTY_ARRAY);
    usageInfos = UsageViewUtil.removeDuplicatedUsages(usageInfos);
    return usageInfos;
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    LOG.assertTrue(myMembersToMove.size() == elements.length);
    myMembersToMove.clear();
    for (PsiElement resolved : elements) {
      myMembersToMove.add((PsiMember)resolved);
    }
  }

  private boolean isInMovedElement(PsiElement element) {
    for (PsiMember member : myMembersToMove) {
      if (PsiTreeUtil.isAncestor(member, element, false)) return true;
    }
    return false;
  }

  @Override
  protected boolean canPerformRefactoringInBranch() {
    return true;
  }

  @Override
  protected void performRefactoring(final UsageInfo @NotNull [] usages) {
    PsiClass targetClass = JavaPsiFacade.getInstance(myProject).findClass(myOptions.getTargetClassName(),
                                                                          GlobalSearchScope.projectScope(myProject));
    if (targetClass == null) return;

    Map<PsiMember, SmartPsiElementPointer<PsiMember>> movedMembers =
      performMove(targetClass, myMembersToMove, ContainerUtil.map(usages, MoveMembersUsageInfo.class::cast));
    afterAllMovements(movedMembers);
  }

  @Override
  protected void performRefactoringInBranch(UsageInfo @NotNull [] originalUsages, ModelBranch branch) {
    PsiClass targetClass = JavaPsiFacade.getInstance(myProject).findClass(myOptions.getTargetClassName(),
                                                                          GlobalSearchScope.projectScope(myProject));
    if (targetClass == null) return;

    PsiClass targetCopy = branch.obtainPsiCopy(targetClass);
    Set<PsiMember> membersToMove = new LinkedHashSet<>(ContainerUtil.map(myMembersToMove, branch::obtainPsiCopy));
    List<MoveMembersUsageInfo> usages = ContainerUtil.map(originalUsages, u -> (MoveMembersUsageInfo)((MoveMembersUsageInfo)u).obtainBranchCopy(branch));

    Map<PsiMember, SmartPsiElementPointer<PsiMember>> movedMembers = performMove(targetCopy, membersToMove, usages);

    branch.runAfterMerge(() -> {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();

      afterAllMovements(EntryStream.of(movedMembers).mapValues(p -> {
        PsiMember member = p.getElement();
        PsiMember original = member == null ? null : branch.findOriginalPsi(member);
        return original == null ? null : SmartPointerManager.createPointer(original);
      }).toMap());
    });
  }

  private Map<PsiMember, SmartPsiElementPointer<PsiMember>> performMove(PsiClass targetClass,
                                                                        Set<PsiMember> membersToMove,
                                                                        List<MoveMembersUsageInfo> usages) {
    // collect anchors to place moved members at
    final Map<PsiMember, SmartPsiElementPointer<PsiElement>> anchors = new HashMap<>();
    final Map<PsiMember, PsiMember> anchorsInSourceClass = new HashMap<>();
    for (PsiMember member : membersToMove) {
      final MoveMemberHandler handler = MoveMemberHandler.EP_NAME.forLanguage(member.getLanguage());
      if (handler != null) {
        final PsiElement anchor = handler.getAnchor(member, targetClass, membersToMove);
        if (anchor instanceof PsiMember && membersToMove.contains((PsiMember)anchor)) {
          anchorsInSourceClass.put(member, (PsiMember)anchor);
        } else {
          anchors.put(member, anchor == null ? null : SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(anchor));
        }
      }
    }

    // correct references to moved members from the outside
    ArrayList<MoveMembersUsageInfo> otherUsages = new ArrayList<>();
    for (MoveMembersUsageInfo usage : usages) {
      if (!usage.reference.isValid()) continue;
      final MoveMemberHandler handler = MoveMemberHandler.EP_NAME.forLanguage(usage.getElement().getLanguage());
      if (handler != null) {
        if (handler.changeExternalUsage(myOptions, usage)) continue;
      }
      otherUsages.add(usage);
    }

    // correct references inside moved members and outer references to Inner Classes
    Map<PsiMember, SmartPsiElementPointer<PsiMember>> movedMembers = new HashMap<>();
    for (PsiMember member : membersToMove) {
      ArrayList<PsiReference> refsToBeRebind = new ArrayList<>();
      for (Iterator<MoveMembersUsageInfo> iterator = otherUsages.iterator(); iterator.hasNext();) {
        MoveMembersUsageInfo info = iterator.next();
        if (member.equals(info.member)) {
          PsiReference ref = info.getReference();
          if (ref != null) {
            refsToBeRebind.add(ref);
          }
          iterator.remove();
        }
      }
      getTransaction().getElementListener(member); // initialize the listener while PSI is valid
      final MoveMemberHandler handler = MoveMemberHandler.EP_NAME.forLanguage(member.getLanguage());
      if (handler != null) {

        SmartPsiElementPointer<? extends PsiElement> anchor;
        if (anchorsInSourceClass.containsKey(member)) {
          final PsiMember memberInSourceClass = anchorsInSourceClass.get(member);
          //anchor should be already moved as myMembersToMove contains members in order they appear in source class
          anchor = memberInSourceClass != null ? movedMembers.get(memberInSourceClass) : null;
        }
        else {
          anchor = anchors.get(member);
        }

        PsiMember newMember = handler.doMove(myOptions, member, anchor == null ? null : anchor.getElement(), targetClass);

        movedMembers.put(member, SmartPointerManager.createPointer(newMember));

        fixModifierList(member, newMember, usages);
        for (PsiReference reference : refsToBeRebind) {
          try {
            reference.bindToElement(newMember);
          }
          catch (Throwable e) {
            LOG.error(e);
          }
        }
      }
    }

    // qualifier info must be decoded after members are moved
    MoveMemberHandler handler = MoveMemberHandler.EP_NAME.forLanguage(targetClass.getLanguage());
    if (handler != null) handler.decodeContextInfo(targetClass);

    return movedMembers;
  }

  private void afterAllMovements(Map<PsiMember, SmartPsiElementPointer<PsiMember>> movedMembers) {
    for (Map.Entry<PsiMember, SmartPsiElementPointer<PsiMember>> entry : movedMembers.entrySet()) {
      PsiMember newMember = entry.getValue().getElement();
      if (newMember != null) {
        PsiMember oldMember = entry.getKey();
        getTransaction().getElementListener(oldMember).elementMoved(newMember);
      }
    }

    myMembersToMove.clear();
    if (myMoveCallback != null) {
      myMoveCallback.refactoringCompleted();
    }

    if (myOpenInEditor) {
      PsiMember item = JBIterable.from(movedMembers.values()).map(SmartPsiElementPointer::getElement).filter(Objects::nonNull).first();
      if (item != null) {
        EditorHelper.openInEditor(item);
      }
    }
  }

  private void fixModifierList(PsiMember member, PsiMember newMember, List<MoveMembersUsageInfo> usages) throws IncorrectOperationException {
    PsiModifierList modifierList = newMember.getModifierList();

    if (modifierList != null && myTargetClass.isInterface()) {
      modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
      modifierList.setModifierProperty(PsiModifier.PROTECTED, false);
      modifierList.setModifierProperty(PsiModifier.PRIVATE, false);
      if (newMember instanceof PsiClass) {
        modifierList.setModifierProperty(PsiModifier.STATIC, false);
      }
      return;
    }

    if (myNewVisibility == null) return;

    final List<UsageInfo> filtered = new ArrayList<>();
    for (MoveMembersUsageInfo usage : usages) {
      if (member == usage.member) {
        filtered.add(usage);
      }
    }
    UsageInfo[] infos = filtered.toArray(UsageInfo.EMPTY_ARRAY);
    VisibilityUtil.fixVisibility(UsageViewUtil.toElements(infos), newMember, myNewVisibility);
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final UsageInfo[] usages = refUsages.get();

    String newVisibility = myOptions.getExplicitMemberVisibility(); // still need to check for access object
    final Map<PsiMember, PsiModifierList> modifierListCopies = new HashMap<>();
    for (PsiMember member : myMembersToMove) {
      PsiModifierList modifierListCopy = member.getModifierList();
      if (modifierListCopy != null) {
        modifierListCopy = (PsiModifierList)modifierListCopy.copy();
      }
      if (modifierListCopy != null && newVisibility != null) {
        try {
          VisibilityUtil.setVisibility(modifierListCopy, newVisibility);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      modifierListCopies.put(member, modifierListCopy);
    }

    analyzeConflictsOnUsages(usages, myMembersToMove, myTargetClass, modifierListCopies, myOptions, conflicts);
    analyzeConflictsOnMembers(myMembersToMove, newVisibility, myTargetClass, modifierListCopies, conflicts);

    RefactoringConflictsUtil.analyzeModuleConflicts(myProject, myMembersToMove, usages, myTargetClass, conflicts);

    return showConflicts(conflicts, usages);
  }

  private static void analyzeConflictsOnUsages(UsageInfo[] usages,
                                               Set<PsiMember> membersToMove,
                                               @NotNull PsiClass targetClass,
                                               Map<PsiMember, PsiModifierList> modifierListCopies,
                                               MoveMembersOptions options,
                                               MultiMap<PsiElement, String> conflicts) {
    for (UsageInfo usage : usages) {
      if (!(usage instanceof MoveMembersUsageInfo)) continue;
      final MoveMembersUsageInfo usageInfo = (MoveMembersUsageInfo)usage;
      final PsiMember member = usageInfo.member;
      final MoveMemberHandler handler = MoveMemberHandler.EP_NAME.forLanguage(member.getLanguage());
      if (handler != null) {
        handler.checkConflictsOnUsage(usageInfo, modifierListCopies.get(member), targetClass, membersToMove, options, conflicts);
      }
    }
  }

  private static void analyzeConflictsOnMembers(Set<PsiMember> membersToMove,
                                                String newVisibility,
                                                PsiClass targetClass,
                                                Map<PsiMember, PsiModifierList> modifierListCopies,
                                                MultiMap<PsiElement, String> conflicts) {
    for (final PsiMember member : membersToMove) {
      final MoveMemberHandler handler = MoveMemberHandler.EP_NAME.forLanguage(member.getLanguage());
      if (handler != null) {
        handler.checkConflictsOnMember(member, newVisibility, modifierListCopies.get(member), targetClass, membersToMove, conflicts);
      }
    }
  }

  @Override
  public void doRun() {
    if (myMembersToMove.isEmpty()){
      String message = RefactoringBundle.message("no.members.selected");
      CommonRefactoringUtil.showErrorMessage(MoveMembersImpl.getRefactoringName(), message, HelpID.MOVE_MEMBERS, myProject);
      return;
    }
    super.doRun();
  }

  public List<PsiElement> getMembers() {
    return new ArrayList<>(myMembersToMove);
  }

  public PsiClass getTargetClass() {
    return myTargetClass;
  }


  public static class MoveMembersUsageInfo extends MoveRenameUsageInfo {
    public final PsiClass qualifierClass;
    public final PsiElement reference;
    public final PsiMember member;

    public MoveMembersUsageInfo(PsiMember member, PsiElement element, PsiClass qualifierClass, PsiElement highlightElement, final PsiReference ref) {
      super(highlightElement, ref, member);
      this.member = member;
      this.qualifierClass = qualifierClass;
      reference = element;
    }

  }
}
