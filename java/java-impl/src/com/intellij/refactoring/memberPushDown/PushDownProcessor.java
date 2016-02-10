/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.memberPushDown;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.codeInsight.intention.impl.CreateSubclassAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.impl.JavaRefactoringListenerManagerImpl;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import kotlin.collections.ArraysKt;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PushDownProcessor extends BaseRefactoringProcessor implements PushDownContext {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.memberPushDown.PushDownProcessor");

  public static final Key<Boolean> REMOVE_QUALIFIER_KEY = Key.create("REMOVE_QUALIFIER_KEY");
  public static final Key<PsiClass> REPLACE_QUALIFIER_KEY = Key.create("REPLACE_QUALIFIER_KEY");

  private final MemberInfo[] myMemberInfos;
  private Set<PsiMember> myMembersToMove = null;
  private PsiClass myClass;
  private final DocCommentPolicy myJavaDocPolicy;
  private CreateClassDialog myCreateClassDlg;

  public PushDownProcessor(Project project,
                           MemberInfo[] memberInfos,
                           PsiClass aClass,
                           DocCommentPolicy javaDocPolicy) {
    super(project);
    myMemberInfos = memberInfos;
    myClass = aClass;
    myJavaDocPolicy = javaDocPolicy;
  }

  @NotNull
  @Override
  public PsiClass getSourceClass() {
    return myClass;
  }

  @NotNull
  @Override
  public DocCommentPolicy<PsiComment> getDocCommentPolicy() {
    //noinspection unchecked
    return myJavaDocPolicy;
  }

  @NotNull
  @Override
  public Set<PsiMember> getMembersToMove() {
    if (myMembersToMove == null) {
      myMembersToMove = ArraysKt.mapTo(myMemberInfos, new LinkedHashSet<>(), new Function1<MemberInfo, PsiMember>() {
        @Override
        public PsiMember invoke(MemberInfo memberInfo) {
          return memberInfo.getMember();
        }
      });
    }

    return myMembersToMove;
  }

  @Override
  protected String getCommandName() {
    return JavaPushDownHandler.REFACTORING_NAME;
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new PushDownUsageViewDescriptor(myClass);
  }

  @Nullable
  @Override
  protected String getRefactoringId() {
    return "refactoring.push.down";
  }

  @Nullable
  @Override
  protected RefactoringEventData getBeforeData() {
    RefactoringEventData data = new RefactoringEventData();
    data.addElement(myClass);
    data.addElements(getMembersToMove());
    return data;
  }

  @Nullable
  @Override
  protected RefactoringEventData getAfterData(@NotNull UsageInfo[] usages) {
    final List<PsiElement> elements = new ArrayList<PsiElement>();
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element instanceof PsiClass) {
        elements.add(element);
      }
    }
    RefactoringEventData data = new RefactoringEventData();
    data.addElements(elements);
    return data;
  }

  @Override
  @NotNull
  protected UsageInfo[] findUsages() {
    final PsiClass[] inheritors = ClassInheritorsSearch.search(myClass, false).toArray(PsiClass.EMPTY_ARRAY);
    final List<UsageInfo> usages = new ArrayList<UsageInfo>(inheritors.length);
    for (PsiClass inheritor : inheritors) {
      usages.add(new UsageInfo(inheritor));
    }

    for (PushDownHelper delegate : PushDownHelper.getAll()) {
      delegate.findUsages(this, usages);
    }

    return usages.toArray(new UsageInfo[usages.size()]);
  }

  @Override
  protected boolean preprocessUsages(@NotNull final Ref<UsageInfo[]> refUsages) {
    final UsageInfo[] usagesIn = refUsages.get();
    final PushDownConflicts pushDownConflicts = new PushDownConflicts(myClass, myMemberInfos);
    pushDownConflicts.checkSourceClassConflicts();

    if (usagesIn.length == 0) {
      if (myClass.isEnum() || myClass.hasModifierProperty(PsiModifier.FINAL)) {
        if (Messages.showOkCancelDialog((myClass.isEnum() ? "Enum " + myClass.getQualifiedName() + " doesn't have constants to inline to. " : "Final class " + myClass.getQualifiedName() + "does not have inheritors. ") +
                                        "Pushing members down will result in them being deleted. " +
                                        "Would you like to proceed?", JavaPushDownHandler.REFACTORING_NAME, Messages.getWarningIcon()) != Messages.OK) {
          return false;
        }
      } else {
        String noInheritors = myClass.isInterface() ?
                              RefactoringBundle.message("interface.0.does.not.have.inheritors", myClass.getQualifiedName()) :
                              RefactoringBundle.message("class.0.does.not.have.inheritors", myClass.getQualifiedName());
        final String message = noInheritors + "\n" + RefactoringBundle.message("push.down.will.delete.members");
        final int answer = Messages.showYesNoCancelDialog(message, JavaPushDownHandler.REFACTORING_NAME, Messages.getWarningIcon());
        if (answer == Messages.YES) {
          myCreateClassDlg = CreateSubclassAction.chooseSubclassToCreate(myClass);
          if (myCreateClassDlg != null) {
            pushDownConflicts.checkTargetClassConflicts(null, false, myCreateClassDlg.getTargetDirectory());
            return showConflicts(pushDownConflicts.getConflicts(), usagesIn);
          } else {
            return false;
          }
        } else if (answer != Messages.NO) return false;
      }
    }
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            for (UsageInfo usage : usagesIn) {
              final PsiElement element = usage.getElement();
              if (element instanceof PsiClass) {
                pushDownConflicts.checkTargetClassConflicts((PsiClass)element, usagesIn.length > 1, element);
              }
            }

            List<UsageInfo> usagesList = ArraysKt.toList(usagesIn);
            for (PushDownHelper delegate : PushDownHelper.getAll()) {
              delegate.findConflicts(PushDownProcessor.this, usagesList, pushDownConflicts.getConflicts());
            }
          }
        });
      }
    };

    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, RefactoringBundle.message("detecting.possible.conflicts"), true, myProject)) {
      return false;
    }

    return showConflicts(pushDownConflicts.getConflicts(), usagesIn);
  }

  @Override
  protected void refreshElements(@NotNull PsiElement[] elements) {
    if(elements.length == 1 && elements[0] instanceof PsiClass) {
      myClass = (PsiClass) elements[0];
    }
    else {
      LOG.assertTrue(false);
    }
  }

  @Override
  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    try {
      encodeRefs();
      if (myCreateClassDlg != null) { //usages.length == 0
        final PsiClass psiClass =
          CreateSubclassAction.createSubclass(myClass, myCreateClassDlg.getTargetDirectory(), myCreateClassDlg.getClassName());
        if (psiClass != null) {
          pushDownToClass(psiClass);
        }
      }
      for (UsageInfo usage : usages) {
        if (usage.getElement() instanceof PsiClass) {
          final PsiClass targetClass = (PsiClass)usage.getElement();
          pushDownToClass(targetClass);
        }
      }
      removeFromTargetClass();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  protected void encodeRefs() {
    final Set<PsiMember> movedMembers = new HashSet<PsiMember>();
    for (MemberInfo memberInfo : myMemberInfos) {
      movedMembers.add(memberInfo.getMember());
    }

    for (MemberInfo memberInfo : myMemberInfos) {
      final PsiMember member = memberInfo.getMember();
      member.accept(new JavaRecursiveElementVisitor() {
        @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
          encodeRef(expression, movedMembers, expression);
          super.visitReferenceExpression(expression);
        }

        @Override public void visitNewExpression(PsiNewExpression expression) {
          final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
          if (classReference != null) {
            encodeRef(classReference, movedMembers, expression);
          }
          super.visitNewExpression(expression);
        }

        @Override
        public void visitTypeElement(final PsiTypeElement type) {
          final PsiJavaCodeReferenceElement referenceElement = type.getInnermostComponentReferenceElement();
          if (referenceElement != null) {
            encodeRef(referenceElement, movedMembers, type);
          }
          super.visitTypeElement(type);
        }
      });
      ChangeContextUtil.encodeContextInfo(member, false);
    }
  }

  private void encodeRef(final PsiJavaCodeReferenceElement expression, final Set<PsiMember> movedMembers, final PsiElement toPut) {
    final PsiElement resolved = expression.resolve();
    if (resolved == null) return;
    final PsiElement qualifier = expression.getQualifier();
    for (PsiMember movedMember : movedMembers) {
      if (movedMember.equals(resolved)) {
        if (qualifier == null) {
          toPut.putCopyableUserData(REMOVE_QUALIFIER_KEY, Boolean.TRUE);
        } else {
          if (qualifier instanceof PsiJavaCodeReferenceElement &&
              ((PsiJavaCodeReferenceElement)qualifier).isReferenceTo(myClass)) {
            toPut.putCopyableUserData(REPLACE_QUALIFIER_KEY, myClass);
          }
        }
      } else if (movedMember instanceof PsiClass && PsiTreeUtil.getParentOfType(resolved, PsiClass.class, false) == movedMember) {
        if (qualifier instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)qualifier).isReferenceTo(movedMember)) {
          toPut.putCopyableUserData(REPLACE_QUALIFIER_KEY, (PsiClass)movedMember);
        }
      } else {
        if (qualifier instanceof PsiThisExpression) {
          final PsiJavaCodeReferenceElement qElement = ((PsiThisExpression)qualifier).getQualifier();
          if (qElement != null && qElement.isReferenceTo(myClass)) {
            toPut.putCopyableUserData(REPLACE_QUALIFIER_KEY, myClass);
          }
        }
      }
    }
  }

  private void removeFromTargetClass() throws IncorrectOperationException {
    for (MemberInfo memberInfo : myMemberInfos) {
      final PsiElement member = memberInfo.getMember();

      if (member instanceof PsiField) {
        member.delete();
      }
      else if (member instanceof PsiMethod) {
        if (memberInfo.isToAbstract()) {
          final PsiMethod method = (PsiMethod)member;
          if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
            PsiUtil.setModifierProperty(method, PsiModifier.PROTECTED, true);
          }
          RefactoringUtil.makeMethodAbstract(myClass, method);
          myJavaDocPolicy.processOldJavaDoc(method.getDocComment());
        }
        else {
          member.delete();
        }
      }
      else if (member instanceof PsiClass) {
        if (Boolean.FALSE.equals(memberInfo.getOverrides())) {
          RefactoringUtil.removeFromReferenceList(myClass.getImplementsList(), (PsiClass)member);
        }
        else {
          member.delete();
        }
      }
    }
  }

  protected void pushDownToClass(PsiClass targetClass) throws IncorrectOperationException {
    PushDownLanguageHelper<MemberInfoBase<? extends PsiMember>> helper =
      PushDownLanguageHelper.forLanguage(targetClass.getLanguage());
    final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(myClass, targetClass, PsiSubstitutor.EMPTY);
    List<PsiMember> newMembers = new SmartList<PsiMember>();
    final List<PsiReference> refsToRebind = new ArrayList<PsiReference>();
    for (MemberInfo memberInfo : myMemberInfos) {
      PsiMember newMember = helper.pushDownToClass(this, memberInfo, targetClass, substitutor, refsToRebind);
      if (newMember != null) {
        newMembers.add(newMember);
      }
    }

    for (PsiMember newMember : newMembers) {
      helper.postprocessMember(this, newMember, targetClass);

      //rebind imports first
      Collections.sort(refsToRebind, new Comparator<PsiReference>() {
        @Override
        public int compare(PsiReference o1, PsiReference o2) {
          return PsiUtil.BY_POSITION.compare(o1.getElement(), o2.getElement());
        }
      });
      for (PsiReference psiReference : refsToRebind) {
        JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(psiReference.bindToElement(newMember));
      }

      final JavaRefactoringListenerManager listenerManager = JavaRefactoringListenerManager.getInstance(newMember.getProject());
      ((JavaRefactoringListenerManagerImpl)listenerManager).fireMemberMoved(myClass, newMember);
    }
  }
}
