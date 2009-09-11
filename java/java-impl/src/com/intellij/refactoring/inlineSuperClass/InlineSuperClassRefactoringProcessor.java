/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.inlineSuperClass.usageInfo.*;
import com.intellij.refactoring.memberPushDown.PushDownConflicts;
import com.intellij.refactoring.memberPushDown.PushDownProcessor;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InlineSuperClassRefactoringProcessor extends FixableUsagesRefactoringProcessor {
  public static final Logger LOG = Logger.getInstance("#" + InlineSuperClassRefactoringProcessor.class.getName());

  private final PsiClass mySuperClass;
  private final PsiClass[] myTargetClasses;
  private final MemberInfo[] myMemberInfos;

  public InlineSuperClassRefactoringProcessor(Project project, PsiClass superClass, final PsiClass... targetClasses) {
    super(project);
    mySuperClass = superClass;
    myTargetClasses = targetClasses;
    MemberInfoStorage memberInfoStorage = new MemberInfoStorage(mySuperClass, new MemberInfo.Filter<PsiMember>() {
      public boolean includeMember(PsiMember element) {
        return true;
      }
    });
    final List<MemberInfo> members = memberInfoStorage.getClassMemberInfos(mySuperClass);
    for (MemberInfo member : members) {
      member.setChecked(true);
    }
    myMemberInfos = members.toArray(new MemberInfo[members.size()]);
  }

  protected UsageViewDescriptor createUsageViewDescriptor(final UsageInfo[] usages) {
    return new InlineSuperClassUsageViewDescriptor(mySuperClass);
  }


  protected void findUsages(@NotNull final List<FixableUsageInfo> usages) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    final PsiElementFactory elementFactory = facade.getElementFactory();
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();
    ReferencesSearch.search(mySuperClass).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference reference) {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiJavaCodeReferenceElement) {
          final PsiImportStaticStatement staticImportStatement = PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class);
          if (staticImportStatement != null) {
            usages.add(new ReplaceStaticImportUsageInfo(staticImportStatement, myTargetClasses));
          } else {
            final PsiImportStatement importStatement = PsiTreeUtil.getParentOfType(element, PsiImportStatement.class);
            if (importStatement != null) {
              usages.add(new RemoveImportUsageInfo(importStatement));
            }
            else {
              final PsiElement parent = element.getParent();
              if (parent instanceof PsiReferenceList) {
                final PsiElement pparent = parent.getParent();
                if (pparent instanceof PsiClass) {
                  final PsiClass inheritor = (PsiClass)pparent;
                  if (parent.equals(inheritor.getExtendsList()) || parent.equals(inheritor.getImplementsList())) {
                    usages.add(new ReplaceExtendsListUsageInfo((PsiJavaCodeReferenceElement)element, mySuperClass, inheritor));
                  }
                }
              } else {
                final PsiClass targetClass = myTargetClasses[0];
                final PsiClassType targetClassType = elementFactory
                  .createType(targetClass, TypeConversionUtil.getSuperClassSubstitutor(mySuperClass, targetClass, PsiSubstitutor.EMPTY));

                if (parent instanceof PsiTypeElement) {
                  final PsiType superClassType = ((PsiTypeElement)parent).getType();
                  PsiSubstitutor subst = getSuperClassSubstitutor(superClassType, targetClassType, resolveHelper, targetClass);
                  usages.add(new ReplaceWithSubtypeUsageInfo(((PsiTypeElement)parent), elementFactory.createType(targetClass, subst), myTargetClasses));
                }
                else if (parent instanceof PsiNewExpression) {
                  final PsiClassType newType = elementFactory.createType(targetClass,
                                                                         getSuperClassSubstitutor(((PsiNewExpression)parent).getType(),
                                                                                                  targetClassType, resolveHelper,
                                                                                                  targetClass));
                  usages.add(new ReplaceConstructorUsageInfo(((PsiNewExpression)parent), newType, myTargetClasses));
                }
                else if (parent instanceof PsiJavaCodeReferenceElement) {
                  usages.add(new ReplaceReferenceUsageInfo(((PsiJavaCodeReferenceElement)parent).getQualifier(), myTargetClasses));
                }
              }
            }
          }
        }
        return true;
      }
    });
    for (PsiClass targetClass : myTargetClasses) {
      for (MemberInfo memberInfo : myMemberInfos) {
        final PsiMember member = memberInfo.getMember();
        for (PsiReference reference : ReferencesSearch.search(member, member.getUseScope(), true)) {
          final PsiElement element = reference.getElement();
          if (element instanceof PsiReferenceExpression &&
              ((PsiReferenceExpression)element).getQualifierExpression() instanceof PsiSuperExpression && PsiTreeUtil.isAncestor(
            targetClass, element, false)) {
            usages.add(new RemoveQualifierUsageInfo((PsiReferenceExpression)element));
          }
        }
      }
    }
  }

  @Override
  protected boolean preprocessUsages(final Ref<UsageInfo[]> refUsages) {
    final List<String> conflicts = new ArrayList<String>();
    final PushDownConflicts pushDownConflicts = new PushDownConflicts(mySuperClass, myMemberInfos);
    for (PsiClass targetClass : myTargetClasses) {
      for (MemberInfo info : myMemberInfos) {
        final PsiMember member = info.getMember();
        pushDownConflicts.checkMemberPlacementInTargetClassConflict(targetClass, member);
      }
    }
    checkConflicts(refUsages, conflicts);
    conflicts.addAll(pushDownConflicts.getConflicts());
    return showConflicts(conflicts);
  }

  @Override
  protected boolean showConflicts(final List<String> conflicts) {
    if (!conflicts.isEmpty() && ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException(StringUtil.join(conflicts, "\n"));
    }
    return super.showConflicts(conflicts);
  }


  protected void performRefactoring(final UsageInfo[] usages) {
    new PushDownProcessor(mySuperClass.getProject(), myMemberInfos, mySuperClass, new DocCommentPolicy(DocCommentPolicy.ASIS)).run();
    replaceInnerTypeUsages();
    super.performRefactoring(usages);
    try {
      mySuperClass.delete();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void replaceInnerTypeUsages() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    final PsiElementFactory elementFactory = facade.getElementFactory();
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();
    final Map<UsageInfo, PsiElement> replacementMap = new HashMap<UsageInfo, PsiElement>();
    for (final PsiClass targetClass : myTargetClasses) {
      final PsiSubstitutor superClassSubstitutor =
        TypeConversionUtil.getSuperClassSubstitutor(mySuperClass, targetClass, PsiSubstitutor.EMPTY);
      final PsiClassType targetClassType = elementFactory.createType(targetClass, superClassSubstitutor);
      targetClass.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitTypeElement(final PsiTypeElement typeElement) {
          super.visitTypeElement(typeElement);
          final PsiType superClassType = typeElement.getType();
          if (PsiUtil.resolveClassInType(superClassType) == mySuperClass) {
            PsiSubstitutor subst = getSuperClassSubstitutor(superClassType, targetClassType, resolveHelper, targetClass);
            replacementMap.put(new UsageInfo(typeElement), elementFactory.createTypeElement(elementFactory.createType(targetClass, subst)));
          }
        }

        @Override
        public void visitNewExpression(final PsiNewExpression expression) {
          super.visitNewExpression(expression);
          final PsiType superClassType = expression.getType();
          if (PsiUtil.resolveClassInType(superClassType) == mySuperClass) {
            PsiSubstitutor subst = getSuperClassSubstitutor(superClassType, targetClassType, resolveHelper, targetClass);
            try {
              replacementMap.put(new UsageInfo(expression), elementFactory.createExpressionFromText("new " + elementFactory.createType(
                targetClass, subst).getCanonicalText() + expression.getArgumentList().getText(), expression));
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      });
    }
    try {
      for (Map.Entry<UsageInfo,PsiElement> elementEntry : replacementMap.entrySet()) {
        final PsiElement element = elementEntry.getKey().getElement();
        if (element != null) {
          element.replace(elementEntry.getValue());
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static PsiSubstitutor getSuperClassSubstitutor(final PsiType superClassType, final PsiClassType targetClassType, final PsiResolveHelper resolveHelper, PsiClass targetClass) {
    PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(targetClass)) {
      subst = subst.put(typeParameter,
                        resolveHelper.getSubstitutionForTypeParameter(typeParameter, targetClassType, superClassType, false,
                                                                      PsiUtil.getLanguageLevel(targetClass)));
    }
    return subst;
  }

  protected String getCommandName() {
    return InlineSuperClassRefactoringHandler.REFACTORING_NAME;
  }
}
