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

package com.intellij.refactoring.inlineSuperClass;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.inlineSuperClass.usageInfo.*;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.memberPushDown.PushDownConflicts;
import com.intellij.refactoring.memberPushDown.PushDownProcessor;
import com.intellij.refactoring.util.*;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InlineSuperClassRefactoringProcessor extends FixableUsagesRefactoringProcessor {
  public static final Logger LOG = Logger.getInstance(InlineSuperClassRefactoringProcessor.class);

  private final PsiClass myCurrentInheritor;
  private final PsiClass mySuperClass;
  private final int myPolicy;
  private PsiClass[] myTargetClasses;
  private final MemberInfo[] myMemberInfos;

  public InlineSuperClassRefactoringProcessor(Project project, PsiClass currentInheritor, PsiClass superClass, int policy) {
    super(project);
    myCurrentInheritor = currentInheritor;
    mySuperClass = superClass;
    myPolicy = policy;
    List<MemberInfo> members = getClassMembersToPush(mySuperClass);
    for (MemberInfo member : members) {
      member.setChecked(true);
    }
    myMemberInfos = members.toArray(new MemberInfo[0]);
  }

  public static List<MemberInfo> getClassMembersToPush(PsiClass superClass) {
    MemberInfoStorage memberInfoStorage = new MemberInfoStorage(superClass, new MemberInfo.Filter<PsiMember>() {
      @Override
      public boolean includeMember(PsiMember element) {
        return !(element instanceof PsiClass) || PsiTreeUtil.isAncestor(superClass, element, true);
      }
    });
    return memberInfoStorage.getClassMemberInfos(superClass);
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(final UsageInfo @NotNull [] usages) {
    return new InlineSuperClassUsageViewDescriptor(mySuperClass);
  }


  @Override
  protected void findUsages(@NotNull final List<FixableUsageInfo> usages) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    final PsiElementFactory elementFactory = facade.getElementFactory();
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();

    if (myCurrentInheritor != null) {
      myTargetClasses = new PsiClass[] {myCurrentInheritor};
    }
    else {
      Collection<PsiClass> inheritors = DirectClassInheritorsSearch.search(mySuperClass).findAll();
      myTargetClasses = inheritors.toArray(PsiClass.EMPTY_ARRAY);
    }

    if (myCurrentInheritor != null) {
      findUsagesInExtendsList(usages, myCurrentInheritor.getExtendsList());
      findUsagesInExtendsList(usages, myCurrentInheritor.getImplementsList());
    }
    else {
      ReferencesSearch.search(mySuperClass).forEach(reference -> {
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
      });
    }
    for (PsiClass targetClass : myTargetClasses) {
      if (skipTargetClass(targetClass)) {
        continue;
      }

      for (MemberInfo memberInfo : myMemberInfos) {
        final PsiMember member = memberInfo.getMember();
        for (PsiReference reference : ReferencesSearch.search(member, member.getUseScope(), true)) {
          final PsiElement element = reference.getElement();
          if (element instanceof PsiReferenceExpression &&
              ((PsiReferenceExpression)element).getQualifierExpression() instanceof PsiSuperExpression &&
              PsiTreeUtil.isAncestor(targetClass, element, false) &&
              !PushDownConflicts.isSuperCallToBeInlined(member, targetClass, mySuperClass, element)) {
            usages.add(new RemoveQualifierUsageInfo((PsiReferenceExpression)element));
          }
        }
      }

      if (!mySuperClass.isInterface()) {
        final PsiMethod[] superConstructors = mySuperClass.getConstructors();
        Set<PsiMethod> addedSuperConstructors = new HashSet<>();
        for (PsiMethod constructor : targetClass.getConstructors()) {
          final PsiCodeBlock constrBody = constructor.getBody();
          PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor);
          if (JavaPsiConstructorUtil.isSuperConstructorCall(call)) {
            final PsiMethod superConstructor = call.resolveMethod();
            if (superConstructor != null && superConstructor.getBody() != null) {
              usages.add(new InlineSuperCallUsageInfo(call));
              processChainedCallsInSuper(superConstructor, targetClass, usages, addedSuperConstructors);
              continue;
            }
          }

          //insert implicit call to super
          for (PsiMethod superConstructor : superConstructors) {
            if (superConstructor.getParameterList().isEmpty()) {
              final PsiExpression expression = JavaPsiFacade.getElementFactory(myProject).createExpressionFromText("super()", constructor);
              usages.add(new InlineSuperCallUsageInfo((PsiMethodCallExpression)expression, constrBody));
              processChainedCallsInSuper(superConstructor, targetClass, usages, addedSuperConstructors);
            }
          }
        }

        if (targetClass.getConstructors().length == 0) {
          //copy default constructor
          for (PsiMethod superConstructor : superConstructors) {
            if (superConstructor.getParameterList().isEmpty()) {
              usages.add(new CopyDefaultConstructorUsageInfo(targetClass, superConstructor));
              break;
            }
          }
        }
      }
    }
  }

  private static void processChainedCallsInSuper(PsiMethod superConstructorWithChain,
                                                 PsiClass targetClass,
                                                 List<? super FixableUsageInfo> usages,
                                                 Set<? super PsiMethod> addedSuperConstructors) {
    addedSuperConstructors.add(superConstructorWithChain);
    PsiMethod chainedConstructor = RefactoringUtil.getChainedConstructor(superConstructorWithChain);
    while (chainedConstructor != null && addedSuperConstructors.add(chainedConstructor)) {
      usages.add(new CopyDefaultConstructorUsageInfo(targetClass, chainedConstructor));
      chainedConstructor = RefactoringUtil.getChainedConstructor(chainedConstructor);
    }
  }

  private void findUsagesInExtendsList(@NotNull List<? super FixableUsageInfo> usages, PsiReferenceList extendsList) {
    final PsiJavaCodeReferenceElement[] referenceExtendsElements = extendsList != null ? extendsList.getReferenceElements() : null;
    if (referenceExtendsElements != null) {
      for (PsiJavaCodeReferenceElement element : referenceExtendsElements) {
        if (mySuperClass.equals(element.resolve())) {
          usages.add(new ReplaceExtendsListUsageInfo(element, mySuperClass, myCurrentInheritor));
        }
      }
    }
  }

  @Override
  protected boolean preprocessUsages(@NotNull final Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final PushDownConflicts pushDownConflicts = new PushDownConflicts(mySuperClass, myMemberInfos, conflicts);
    for (PsiClass targetClass : myTargetClasses) {
      if (targetClass instanceof PsiAnonymousClass) {
        conflicts.putValue(targetClass, "Cannot inline into anonymous class.");
      }
      else if (PsiTreeUtil.isAncestor(mySuperClass, targetClass, false)) {
        conflicts.putValue(targetClass, "Cannot inline into the inner class. Move \'" + targetClass.getName() + "\' to upper level");
      }
      else {
        for (MemberInfo info : myMemberInfos) {
          final PsiMember member = info.getMember();
          pushDownConflicts.checkMemberPlacementInTargetClassConflict(targetClass, member);
          if (myCurrentInheritor == null) { //superclass to be removed
            Set<PsiMember> movedMembers = new HashSet<>(pushDownConflicts.getMovedMembers());
            movedMembers.addAll(Arrays.asList(mySuperClass.getConstructors()));
            RefactoringConflictsUtil
              .analyzeAccessibilityConflicts(movedMembers, targetClass, conflicts, null, targetClass, pushDownConflicts.getAbstractMembers());
          }
        }
      }
    }
    if (myCurrentInheritor != null) {
      ReferencesSearch.search(myCurrentInheritor).forEach(reference -> {
        final PsiElement element = reference.getElement();
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiNewExpression) {
          final PsiClass aClass = PsiUtil.resolveClassInType(getPlaceExpectedType(parent));
          if (aClass == mySuperClass) {
            conflicts.putValue(parent, "Instance of target type is passed to a place where super class is expected.");
            return false;
          }
        }
        return true;
      });
    }
    checkConflicts(refUsages, conflicts);
    return showConflicts(conflicts, refUsages.get());
  }

  private boolean skipTargetClass(PsiClass targetClass) {
    return targetClass instanceof PsiAnonymousClass ||
           PsiTreeUtil.isAncestor(mySuperClass, targetClass, false);
  }

  @Nullable
  private static PsiType getPlaceExpectedType(PsiElement parent) {
    PsiType type = PsiTypesUtil.getExpectedTypeByParent(parent);
    if (type == null) {
      final PsiElement arg = PsiUtil.skipParenthesizedExprUp(parent);
      final PsiElement gParent = arg.getParent();
      if (gParent instanceof PsiExpressionList) {
        int i = ArrayUtilRt.find(((PsiExpressionList)gParent).getExpressions(), arg);
        final PsiElement pParent = gParent.getParent();
        if (pParent instanceof PsiCallExpression) {
          final PsiMethod method = ((PsiCallExpression)pParent).resolveMethod();
          if (method != null) {
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            if (i >= parameters.length) {
              if (method.isVarArgs()) {
                return ((PsiEllipsisType)parameters[parameters.length - 1].getType()).getComponentType();
              }
            } else {
              return parameters[i].getType();
            }
          }
        }
      }
    }
    return type;
  }

  @Override
  protected void performRefactoring(final UsageInfo @NotNull [] usages) {
    try {
      final UsageInfo[] infos = ContainerUtil.map2Array(myTargetClasses, UsageInfo.class, UsageInfo::new);
      new PushDownProcessor<>(mySuperClass, Arrays.asList(myMemberInfos), new DocCommentPolicy(myPolicy)).pushDownToClasses(infos);

      CommonRefactoringUtil.sortDepthFirstRightLeftOrder(usages);
      for (UsageInfo usageInfo : usages) {
        if (!(usageInfo instanceof ReplaceExtendsListUsageInfo || usageInfo instanceof RemoveImportUsageInfo)) {
          try {
            ((FixableUsageInfo)usageInfo).fixUsage();
          }
          catch (IncorrectOperationException e) {
            LOG.info(e);
          }
        }
      }

      replaceInnerTypeUsages();

      //postpone broken hierarchy
      for (UsageInfo usage : usages) {
        if (usage instanceof ReplaceExtendsListUsageInfo || usage instanceof RemoveImportUsageInfo) {
          ((FixableUsageInfo)usage).fixUsage();
        }
      }

      //delete the class if all refs replaced
      if (myCurrentInheritor == null) {
        mySuperClass.delete();
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  @Override
  protected RefactoringEventData getBeforeData() {
    final RefactoringEventData data = new RefactoringEventData();
    data.addElement(mySuperClass);
    data.addElements(myTargetClasses);
    return data;
  }

  @Nullable
  @Override
  protected RefactoringEventData getAfterData(UsageInfo @NotNull [] usages) {
    final RefactoringEventData data = new RefactoringEventData();
    data.addElements(myTargetClasses);
    return data;
  }

  @Nullable
  @Override
  protected String getRefactoringId() {
    return "refactoring.inline.class";
  }

  @NotNull
  @Override
  protected Collection<? extends PsiElement> getElementsToWrite(@NotNull UsageViewDescriptor descriptor) {
    return myCurrentInheritor != null ? Collections.emptyList()
                                      : super.getElementsToWrite(descriptor);
  }

  private void replaceInnerTypeUsages() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    final PsiElementFactory elementFactory = facade.getElementFactory();
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();
    final Map<UsageInfo, PsiElement> replacementMap = new HashMap<>();
    for (final PsiClass targetClass : myTargetClasses) {
      if (skipTargetClass(targetClass)) continue;
      final PsiSubstitutor superClassSubstitutor =
        TypeConversionUtil.getSuperClassSubstitutor(mySuperClass, targetClass, PsiSubstitutor.EMPTY);
      final PsiClassType targetClassType = elementFactory.createType(targetClass, superClassSubstitutor);
      targetClass.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitTypeElement(final PsiTypeElement typeElement) {
          super.visitTypeElement(typeElement);
          final PsiType superClassType = typeElement.getType();
          if (PsiUtil.resolveClassInClassTypeOnly(superClassType) == mySuperClass) {
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
              final String typeCanonicalText = elementFactory.createType(targetClass, subst).getCanonicalText();
              final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
              if (classReference != null) {
                replacementMap.put(new UsageInfo(classReference), elementFactory.createReferenceFromText(typeCanonicalText, expression));
              }
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }

        @Override
        public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          super.visitReferenceElement(reference);
          if (reference.resolve() == mySuperClass && PsiTreeUtil.getParentOfType(reference, PsiComment.class) != null) {
            replacementMap.put(new UsageInfo(reference), elementFactory.createClassReferenceElement(targetClass));
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

  @Override
  @NotNull
  protected String getCommandName() {
    return InlineSuperClassRefactoringHandler.REFACTORING_NAME;
  }
}
