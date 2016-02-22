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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
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
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.impl.JavaRefactoringListenerManagerImpl;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PushDownProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.memberPushDown.PushDownProcessor");

  private final MemberInfo[] myMemberInfos;
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

  @Override
  protected String getCommandName() {
    return JavaPushDownHandler.REFACTORING_NAME;
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new PushDownUsageViewDescriptor<MemberInfo>(myClass, myMemberInfos);
  }

  @NotNull
  @Override
  protected Collection<? extends PsiElement> getElementsToWrite(@NotNull UsageViewDescriptor descriptor) {
    return Collections.singletonList(myClass);
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
    data.addMembers(myMemberInfos, new Function<MemberInfo, PsiElement>() {
      @Override
      public PsiElement fun(MemberInfo info) {
        return info.getMember();
      }
    });
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

    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(myClass);
    if (interfaceMethod != null && isMoved(interfaceMethod)) {
      FunctionalExpressionSearch.search(myClass).forEach(new Processor<PsiFunctionalExpression>() {
        @Override
        public boolean process(PsiFunctionalExpression expression) {
          usages.add(new UsageInfo(expression));
          return true;
        }
      });
    }

    return usages.toArray(new UsageInfo[usages.size()]);
  }

  private boolean isMoved(PsiMember member) {
    for (MemberInfo info : myMemberInfos) {
      if (member == info.getMember()) {
        return true;
      }
    }
    return false;
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
          }
        });
      }
    };

    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, RefactoringBundle.message("detecting.possible.conflicts"), true, myProject)) {
      return false;
    }

    for (UsageInfo info : usagesIn) {
      final PsiElement element = info.getElement();
      if (element instanceof PsiFunctionalExpression) {
        pushDownConflicts.getConflicts().putValue(element, RefactoringBundle.message("functional.interface.broken"));
      }
    }
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(myClass, CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE);
    if (annotation != null && isMoved(LambdaUtil.getFunctionalInterfaceMethod(myClass))) {
      pushDownConflicts.getConflicts().putValue(annotation, RefactoringBundle.message("functional.interface.broken"));
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

  private static final Key<Boolean> REMOVE_QUALIFIER_KEY = Key.create("REMOVE_QUALIFIER_KEY");
  private static final Key<PsiClass> REPLACE_QUALIFIER_KEY = Key.create("REPLACE_QUALIFIER_KEY");

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

  private void decodeRefs(final PsiMember member, final PsiClass targetClass) {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    member.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        decodeRef(expression, factory, targetClass, expression);
        super.visitReferenceExpression(expression);
      }

      @Override public void visitNewExpression(PsiNewExpression expression) {
        final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
        if (classReference != null) decodeRef(classReference, factory, targetClass, expression);
        super.visitNewExpression(expression);
      }

      @Override
      public void visitTypeElement(final PsiTypeElement type) {
        final PsiJavaCodeReferenceElement referenceElement = type.getInnermostComponentReferenceElement();
        if (referenceElement != null)  decodeRef(referenceElement, factory, targetClass, type);
        super.visitTypeElement(type);
      }
    });
  }

  private void decodeRef(final PsiJavaCodeReferenceElement ref,
                         final PsiElementFactory factory,
                         final PsiClass targetClass,
                         final PsiElement toGet) {
    try {
      if (toGet.getCopyableUserData(REMOVE_QUALIFIER_KEY) != null) {
        toGet.putCopyableUserData(REMOVE_QUALIFIER_KEY, null);
        final PsiElement qualifier = ref.getQualifier();
        if (qualifier != null) qualifier.delete();
      }
      else {
        PsiClass psiClass = toGet.getCopyableUserData(REPLACE_QUALIFIER_KEY);
        if (psiClass != null) {
          toGet.putCopyableUserData(REPLACE_QUALIFIER_KEY, null);
          PsiElement qualifier = ref.getQualifier();
          if (qualifier != null) {

            if (psiClass == myClass) {
              psiClass = targetClass;
            } else if (psiClass.getContainingClass() == myClass) {
              psiClass = targetClass.findInnerClassByName(psiClass.getName(), false);
              LOG.assertTrue(psiClass != null);
            }

            if (!(qualifier instanceof PsiThisExpression) && ref instanceof PsiReferenceExpression) {
              ((PsiReferenceExpression)ref).setQualifierExpression(factory.createReferenceExpression(psiClass));
            }
            else {
              if (qualifier instanceof PsiThisExpression) {
                qualifier = ((PsiThisExpression)qualifier).getQualifier();
              }
              qualifier.replace(factory.createReferenceElementByType(factory.createType(psiClass)));
            }
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
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
    final PsiElementFactory factory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
    final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(myClass, targetClass, PsiSubstitutor.EMPTY);
    for (MemberInfo memberInfo : myMemberInfos) {
      PsiMember member = memberInfo.getMember();
      final List<PsiReference> refsToRebind = new ArrayList<PsiReference>();
      final PsiModifierList list = member.getModifierList();
      LOG.assertTrue(list != null);
      if (list.hasModifierProperty(PsiModifier.STATIC)) {
        for (final PsiReference reference : ReferencesSearch.search(member)) {
          final PsiElement element = reference.getElement();
          if (element instanceof PsiReferenceExpression) {
            final PsiExpression qualifierExpression = ((PsiReferenceExpression)element).getQualifierExpression();
            if (qualifierExpression instanceof PsiReferenceExpression && !(((PsiReferenceExpression)qualifierExpression).resolve() instanceof PsiClass)) {
              continue;
            }
          }
          refsToRebind.add(reference);
        }
      }
      member = (PsiMember)member.copy();
      RefactoringUtil.replaceMovedMemberTypeParameters(member, PsiUtil.typeParametersIterable(myClass), substitutor, factory);
      PsiMember newMember = null;
      if (member instanceof PsiField) {
        ((PsiField)member).normalizeDeclaration();
        if (myClass.isInterface() && !targetClass.isInterface()) {
          PsiUtil.setModifierProperty(member, PsiModifier.PUBLIC, true);
          PsiUtil.setModifierProperty(member, PsiModifier.STATIC, true);
          PsiUtil.setModifierProperty(member, PsiModifier.FINAL, true);
        }
        newMember = (PsiMember)targetClass.add(member);
      }
      else if (member instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)member;
        PsiMethod methodBySignature = MethodSignatureUtil.findMethodBySuperSignature(targetClass, method.getSignature(substitutor), false);
        if (methodBySignature == null) {
          newMember = (PsiMethod)targetClass.add(method);
          if (myClass.isInterface()) {
            if (!targetClass.isInterface()) {
              PsiUtil.setModifierProperty(newMember, PsiModifier.PUBLIC, true);
              if (newMember.hasModifierProperty(PsiModifier.DEFAULT)) {
                PsiUtil.setModifierProperty(newMember, PsiModifier.DEFAULT, false);
              }
              else {
                PsiUtil.setModifierProperty(newMember, PsiModifier.ABSTRACT, true);
              }
            }
          }
          else if (memberInfo.isToAbstract()) {
            if (newMember.hasModifierProperty(PsiModifier.PRIVATE)) {
              PsiUtil.setModifierProperty(newMember, PsiModifier.PROTECTED, true);
            }
            myJavaDocPolicy.processNewJavaDoc(((PsiMethod)newMember).getDocComment());
          }
          if (memberInfo.isToAbstract()) {
            OverrideImplementUtil.annotateOnOverrideImplement((PsiMethod)newMember, targetClass, (PsiMethod)memberInfo.getMember());
          }
        }
        else { //abstract method: remove @Override
          final PsiAnnotation annotation = AnnotationUtil.findAnnotation(methodBySignature, "java.lang.Override");
          if (annotation != null && !leaveOverrideAnnotation(substitutor, method)) {
            annotation.delete();
          }
          final PsiDocComment oldDocComment = method.getDocComment();
          if (oldDocComment != null) {
            final PsiDocComment docComment = methodBySignature.getDocComment();
            final int policy = myJavaDocPolicy.getJavaDocPolicy();
            if (policy == DocCommentPolicy.COPY || policy == DocCommentPolicy.MOVE) {
              if (docComment != null) {
                docComment.replace(oldDocComment);
              }
              else {
                methodBySignature.getParent().addBefore(oldDocComment, methodBySignature);
              }
            }
          }
        }
      }
      else if (member instanceof PsiClass) {
        if (Boolean.FALSE.equals(memberInfo.getOverrides())) {
          final PsiClass aClass = (PsiClass)memberInfo.getMember();
          PsiClassType classType = null;
          if (!targetClass.isInheritor(aClass, false)) {
            final PsiClassType[] types = memberInfo.getSourceReferenceList().getReferencedTypes();
            for (PsiClassType type : types) {
              if (type.resolve() == aClass) {
                classType = (PsiClassType)substitutor.substitute(type);
              }
            }
            PsiJavaCodeReferenceElement classRef = classType != null ? factory.createReferenceElementByType(classType) : factory.createClassReferenceElement(aClass);
            if (aClass.isInterface()) {
              targetClass.getImplementsList().add(classRef);
            } else {
              targetClass.getExtendsList().add(classRef);
            }
          }
        }
        else {
          newMember = (PsiMember)targetClass.add(member);
        }
      }

      if (newMember != null) {
        decodeRefs(newMember, targetClass);
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

  private boolean leaveOverrideAnnotation(PsiSubstitutor substitutor, PsiMethod method) {
    final PsiMethod methodBySignature = MethodSignatureUtil.findMethodBySignature(myClass, method.getSignature(substitutor), false);
    if (methodBySignature == null) return false;
    final PsiMethod[] superMethods = methodBySignature.findDeepestSuperMethods();
    if (superMethods.length == 0) return false;
    final boolean is15 = !PsiUtil.isLanguageLevel6OrHigher(methodBySignature);
    if (is15) {
      for (PsiMethod psiMethod : superMethods) {
        final PsiClass aClass = psiMethod.getContainingClass();
        if (aClass != null && aClass.isInterface()) {
          return false;
        }
      }
    }
    return true;
  }
}
