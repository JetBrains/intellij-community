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

/**
 * created at Sep 11, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move.moveMembers;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.refactoring.move.MoveMemberViewDescriptor;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MoveMembersProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveMembers.MoveMembersProcessor");

  private PsiClass myTargetClass;
  private final Set<PsiMember> myMembersToMove = new LinkedHashSet<PsiMember>();
  private final MoveCallback myMoveCallback;
  private String myNewVisibility; // "null" means "as is"
  private String myCommandName = MoveMembersImpl.REFACTORING_NAME;
  private boolean myMakeEnumConstant;
  private MoveMembersOptions myOptions;

  public MoveMembersProcessor(Project project, MoveCallback moveCallback, MoveMembersOptions options) {
    super(project);
    myMoveCallback = moveCallback;
    setOptions(options);
  }

  public MoveMembersProcessor(Project project, MoveMembersOptions options) {
    this(project, null, options);
  }

  protected String getCommandName() {
    return myCommandName;
  }

  private void setOptions(MoveMembersOptions dialog) {
    myOptions = dialog;
    PsiMember[] members = dialog.getSelectedMembers();
    myMembersToMove.clear();
    ContainerUtil.addAll(myMembersToMove, members);

    setCommandName(members);

    final PsiManager manager = PsiManager.getInstance(myProject);
    myTargetClass =
      JavaPsiFacade.getInstance(manager.getProject()).findClass(dialog.getTargetClassName(), GlobalSearchScope.projectScope(myProject));
    myNewVisibility = dialog.getMemberVisibility();
    myMakeEnumConstant = dialog.makeEnumConstant();
  }

  private void setCommandName(final PsiMember[] members) {
    StringBuilder commandName = new StringBuilder();
    commandName.append(MoveHandler.REFACTORING_NAME);
    commandName.append(" ");
    boolean first = true;
    for (PsiMember member : members) {
      if (!first) commandName.append(", ");
      commandName.append(UsageViewUtil.getType(member));
      commandName.append(' ');
      commandName.append(UsageViewUtil.getShortName(member));
      first = false;
    }

    myCommandName = commandName.toString();
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new MoveMemberViewDescriptor(myMembersToMove.toArray(new PsiElement[myMembersToMove.size()]));
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    final List<UsageInfo> usagesList = new ArrayList<UsageInfo>();
    for (PsiMember member : myMembersToMove) {
      for (PsiReference psiReference : ReferencesSearch.search(member)) {
        PsiElement ref = psiReference.getElement();
        final MoveMemberHandler handler = MoveMemberHandler.EP_NAME.forLanguage(ref.getLanguage());
        MoveMembersUsageInfo usage = null;
        if (handler != null) {
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
    UsageInfo[] usageInfos = usagesList.toArray(new UsageInfo[usagesList.size()]);
    usageInfos = UsageViewUtil.removeDuplicatedUsages(usageInfos);
    return usageInfos;
  }

  protected void refreshElements(PsiElement[] elements) {
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

  protected void performRefactoring(final UsageInfo[] usages) {
    try {
      // correct references to moved members from the outside
      PsiClass targetClass = JavaPsiFacade.getInstance(myProject)
      .findClass(myOptions.getTargetClassName(), GlobalSearchScope.projectScope(myProject));
      if (targetClass == null) return;
      final Map<PsiMember, PsiElement> anchors = new HashMap<PsiMember, PsiElement>();
      for (PsiMember member : myMembersToMove) {
        final MoveMemberHandler handler = MoveMemberHandler.EP_NAME.forLanguage(member.getLanguage());
        if (handler != null) {
          anchors.put(member, handler.getAnchor(member, targetClass));
        }
      }
      ArrayList<MoveMembersUsageInfo> otherUsages = new ArrayList<MoveMembersUsageInfo>();
      for (UsageInfo usageInfo : usages) {
        MoveMembersUsageInfo usage = (MoveMembersUsageInfo)usageInfo;
        if (!usage.reference.isValid()) continue;
        final MoveMemberHandler handler = MoveMemberHandler.EP_NAME.forLanguage(usageInfo.getElement().getLanguage());
        if (handler!=null) {
          if (handler.changeExternalUsage(myOptions, usage)) continue;
        }
        otherUsages.add(usage);
      }

      // correct references inside moved members and outer references to Inner Classes
      for (PsiMember member : myMembersToMove) {
        ArrayList<PsiReference> refsToBeRebind = new ArrayList<PsiReference>();
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
        final RefactoringElementListener elementListener = getTransaction().getElementListener(member);
        final MoveMemberHandler handler = MoveMemberHandler.EP_NAME.forLanguage(member.getLanguage());
        if (handler != null) {
          PsiMember newMember=handler.doMove(myOptions, member, anchors.get(member), targetClass);
          elementListener.elementMoved(newMember);

          fixModifierList(newMember, usages);
          for (PsiReference reference : refsToBeRebind) {
            reference.bindToElement(newMember);
          }
        }
      }

      // qualifier info must be decoded after members are moved
      //ChangeContextUtil.decodeContextInfo(myTargetClass, null, null);
      final MoveMemberHandler handler = MoveMemberHandler.EP_NAME.forLanguage(myTargetClass.getLanguage());
      if (handler != null) handler.decodeContextInfo(myTargetClass);

      myMembersToMove.clear();
      if (myMoveCallback != null) {
        myMoveCallback.refactoringCompleted();
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void fixModifierList(PsiMember newMember, final UsageInfo[] usages) throws IncorrectOperationException {
    PsiModifierList modifierList = newMember.getModifierList();

    if(myTargetClass.isInterface()) {
      modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
      modifierList.setModifierProperty(PsiModifier.PROTECTED, false);
      modifierList.setModifierProperty(PsiModifier.PRIVATE, false);
      if (newMember instanceof PsiClass) {
        modifierList.setModifierProperty(PsiModifier.STATIC, false);
      }
      return;
    }

    if(myNewVisibility == null) return;

    VisibilityUtil.fixVisibility(usages, newMember, myNewVisibility);
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    final UsageInfo[] usages = refUsages.get();
    try {
      addInaccessiblleConflicts(conflicts, usages);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    analyzeMoveConflicts(myMembersToMove, myTargetClass, myNewVisibility, conflicts);
    RefactoringConflictsUtil.analyzeModuleConflicts(myProject, myMembersToMove, usages, myTargetClass, conflicts);
    return showConflicts(conflicts, usages);
  }

  private void addInaccessiblleConflicts(final MultiMap<PsiElement, String> conflicts, final UsageInfo[] usages) throws IncorrectOperationException {
    String newVisibility = myNewVisibility;
    if (VisibilityUtil.ESCALATE_VISIBILITY.equals(newVisibility)) { //Still need to check for access object
      newVisibility = PsiModifier.PUBLIC;
    }

    Map<PsiMember, PsiModifierList> modifierListCopies = new HashMap<PsiMember, PsiModifierList>();
    for (PsiMember member : myMembersToMove) {
      PsiModifierList copy = member.getModifierList();
      if (copy!=null) copy= (PsiModifierList)copy.copy();
      if (newVisibility != null) {
        if (copy!=null) VisibilityUtil.setVisibility(copy, newVisibility);
      }
      modifierListCopies.put(member, copy);
    }

    for (UsageInfo usage : usages) {
      if (usage instanceof MoveMembersUsageInfo) {
        final MoveMembersUsageInfo usageInfo = (MoveMembersUsageInfo)usage;
        PsiElement element = usage.getElement();
        if (element != null) {
          final PsiMember member = usageInfo.member;
          if (element instanceof PsiReferenceExpression) {
            PsiExpression qualifier = ((PsiReferenceExpression)element).getQualifierExpression();
            PsiClass accessObjectClass = null;
            if (qualifier != null) {
              accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
            }

            if (!JavaResolveUtil.isAccessible(member, myTargetClass, modifierListCopies.get(member), element, accessObjectClass, null)) {
              newVisibility = newVisibility == null ? VisibilityUtil.getVisibilityStringToDisplay(member) : newVisibility;
              String message =
                CommonRefactoringUtil.capitalize(RefactoringBundle.message("0.with.1.visibility.is.not.accesible.from.2", RefactoringUIUtil.getDescription(member, false),
                                          newVisibility, RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(element), true)));
              conflicts.putValue(member, message);
            }
          }

          if (member instanceof PsiField && myTargetClass.isInterface()) {
            final ReadWriteAccessDetector accessDetector = ReadWriteAccessDetector.findDetector(member);
            if (accessDetector != null) {
              final ReadWriteAccessDetector.Access access = accessDetector.getExpressionAccess(element);
              if (access != ReadWriteAccessDetector.Access.Read) {
                conflicts.putValue(element, CommonRefactoringUtil.capitalize(RefactoringUIUtil.getDescription(member, true)) + " has write access but is moved to an interface");
              }
            }
          }
        }
      }
    }
  }

  public void doRun() {
    if (myMembersToMove.isEmpty()){
      String message = RefactoringBundle.message("no.members.selected");
      CommonRefactoringUtil.showErrorMessage(MoveMembersImpl.REFACTORING_NAME, message, HelpID.MOVE_MEMBERS, myProject);
      return;
    }
    super.doRun();
  }

  private static void analyzeMoveConflicts(@NotNull Set<PsiMember> membersToMove, final PsiClass targetClass, final String newVisibility,
                                                              MultiMap<PsiElement, String> conflicts) {
    for (final PsiMember member : membersToMove) {
      if (member instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)member;
        if (hasMethod(targetClass, method)) {
          String message = RefactoringBundle.message("0.already.exists.in.the.target.class", RefactoringUIUtil.getDescription(method, false));
          message = CommonRefactoringUtil.capitalize(message);
          conflicts.putValue(method, message);
        }
      }
      else if (member instanceof PsiField) {
        PsiField field = (PsiField)member;
        if (hasField(targetClass, field)) {
          String message = RefactoringBundle.message("0.already.exists.in.the.target.class", RefactoringUIUtil.getDescription(field, false));
          message = CommonRefactoringUtil.capitalize(message);
          conflicts.putValue(field, message);
        }
      }
    }
    RefactoringConflictsUtil.analyzeAccessibilityConflicts(membersToMove, targetClass, conflicts, newVisibility);
  }

  private static boolean hasMethod(PsiClass targetClass, PsiMethod method) {
    PsiMethod[] targetClassMethods = targetClass.getMethods();
    for (PsiMethod method1 : targetClassMethods) {
      if (MethodSignatureUtil.areSignaturesEqual(method.getSignature(PsiSubstitutor.EMPTY),
                                                 method1.getSignature(PsiSubstitutor.EMPTY))) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasField(PsiClass targetClass, PsiField field) {
    String fieldName = field.getName();
    PsiField[] targetClassFields = targetClass.getFields();
    for (PsiField targetClassField : targetClassFields) {
      if (fieldName.equals(targetClassField.getName())) {
        return true;
      }
    }
    return false;
  }

  public List<PsiElement> getMembers() {
    return new ArrayList<PsiElement>(myMembersToMove);
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
