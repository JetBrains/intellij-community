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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 14.06.2002
 * Time: 22:35:19
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.memberPullUp;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.impl.JavaRefactoringListenerManagerImpl;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PullUpHelper extends BaseRefactoringProcessor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.memberPullUp.PullUpHelper");
  private static final Key<Boolean> PRESERVE_QUALIFIER = Key.<Boolean>create("PRESERVE_QUALIFIER");
  private final PsiClass mySourceClass;
  private final PsiClass myTargetSuperClass;
  private final boolean myIsTargetInterface;
  private final MemberInfo[] myMembersToMove;
  private final DocCommentPolicy myJavaDocPolicy;
  private HashSet<PsiMember> myMembersAfterMove = null;
  private final PsiManager myManager;


  public PullUpHelper(PsiClass sourceClass, PsiClass targetSuperClass, MemberInfo[] membersToMove,
                      DocCommentPolicy javaDocPolicy) {
    super(sourceClass.getProject());
    mySourceClass = sourceClass;
    myTargetSuperClass = targetSuperClass;
    myMembersToMove = membersToMove;
    myJavaDocPolicy = javaDocPolicy;
    myIsTargetInterface = targetSuperClass.isInterface();
    myManager = mySourceClass.getManager();
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new PullUpUsageViewDescriptor();
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    return new UsageInfo[0];
  }

  protected void refreshElements(PsiElement[] elements) {
  }

  protected void performRefactoring(UsageInfo[] usages) {
    moveMembersToBase();
    moveFieldInitializations();
  }

  protected String getCommandName() {
    return RefactoringBundle.message("pullUp.command", UsageViewUtil.getDescriptiveName(mySourceClass));
  }

  public void moveMembersToBase()
          throws IncorrectOperationException {
    final HashSet<PsiMember> movedMembers = new HashSet<PsiMember>();
    myMembersAfterMove = new HashSet<PsiMember>();

    // build aux sets
    for (MemberInfo info : myMembersToMove) {
      movedMembers.add(info.getMember());
    }

    // correct private member visibility
    for (MemberInfo info : myMembersToMove) {
      if (info.getMember() instanceof PsiClass && info.getOverrides() != null) continue;
      PsiModifierListOwner modifierListOwner = info.getMember();
      if (myIsTargetInterface) {
        PsiUtil.setModifierProperty(modifierListOwner, PsiModifier.PUBLIC, true);
      }
      else if (modifierListOwner.hasModifierProperty(PsiModifier.PRIVATE)) {
        if (info.isToAbstract() || willBeUsedInSubclass(modifierListOwner, movedMembers, myTargetSuperClass, mySourceClass)) {
          PsiUtil.setModifierProperty(modifierListOwner, PsiModifier.PROTECTED, true);
        }
      }
      ChangeContextUtil.encodeContextInfo(info.getMember(), true);
    }

    final PsiSubstitutor substitutor = upDownSuperClassSubstitutor();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);

    // do actual move
    for (MemberInfo info : myMembersToMove) {
      if (info.getMember() instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)info.getMember();
        final boolean isOriginalMethodAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
        if (myIsTargetInterface || info.isToAbstract()) {
          PsiMethod methodCopy = (PsiMethod)method.copy();
          ChangeContextUtil.clearContextInfo(method);
          RefactoringUtil.abstractizeMethod(myTargetSuperClass, methodCopy);
          RefactoringUtil.replaceMovedMemberTypeParameters(methodCopy, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
          if (method.findDeepestSuperMethods().length == 0 || (myTargetSuperClass.isInterface() && !PsiUtil.isLanguageLevel6OrHigher(mySourceClass))) {
            deleteOverrideAnnotationIfFound(methodCopy);
          }

          myJavaDocPolicy.processCopiedJavaDoc(methodCopy.getDocComment(), method.getDocComment(), isOriginalMethodAbstract);

          final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(methodCopy);
          CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(method.getProject());
          if (styleSettings.INSERT_OVERRIDE_ANNOTATION) {
            if (PsiUtil.isLanguageLevel5OrHigher(mySourceClass) && !myTargetSuperClass.isInterface() || PsiUtil.isLanguageLevel6OrHigher(mySourceClass)) {
              new AddAnnotationFix(Override.class.getName(), method).invoke(method.getProject(), null, mySourceClass.getContainingFile());
            }
          }
          if (!PsiUtil.isLanguageLevel6OrHigher(mySourceClass) && myTargetSuperClass.isInterface()) {
            if (isOriginalMethodAbstract) {
              for (PsiMethod oMethod : OverridingMethodsSearch.search(method)) {
                deleteOverrideAnnotationIfFound(oMethod);
              }
            }
            deleteOverrideAnnotationIfFound(method);
          }
          myMembersAfterMove.add(movedElement);
          if (isOriginalMethodAbstract) {
            method.delete();
          }
        }
        else {
          if (isOriginalMethodAbstract) {
            PsiUtil.setModifierProperty(myTargetSuperClass, PsiModifier.ABSTRACT, true);
          }
          RefactoringUtil.replaceMovedMemberTypeParameters(method, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
          fixReferencesToStatic(method, movedMembers);
          final PsiMethod superClassMethod = myTargetSuperClass.findMethodBySignature(method, false);
          if (superClassMethod != null && superClassMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
            superClassMethod.replace(method);
          }
          else {
            final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(method);
            myMembersAfterMove.add(movedElement);
          }
          method.delete();
        }
      }
      else if (info.getMember() instanceof PsiField) {
        PsiField field = (PsiField)info.getMember();
        field.normalizeDeclaration();
        RefactoringUtil.replaceMovedMemberTypeParameters(field, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
        fixReferencesToStatic(field, movedMembers);
        if (myIsTargetInterface) {
          PsiUtil.setModifierProperty(field, PsiModifier.PUBLIC, true);
        }
        final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(field);
        myMembersAfterMove.add(movedElement);
        field.delete();
      }
      else if (info.getMember() instanceof PsiClass) {
        PsiClass aClass = (PsiClass)info.getMember();
        if (Boolean.FALSE.equals(info.getOverrides())) {
          final PsiReferenceList sourceReferenceList = info.getSourceReferenceList();
          LOG.assertTrue(sourceReferenceList != null);
          PsiJavaCodeReferenceElement ref = mySourceClass.equals(sourceReferenceList.getParent()) ?
                                            RefactoringUtil.removeFromReferenceList(sourceReferenceList, aClass) :
                                            RefactoringUtil.findReferenceToClass(sourceReferenceList, aClass);
          if (ref != null) {
            RefactoringUtil.replaceMovedMemberTypeParameters(ref, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
            final PsiReferenceList referenceList =
              myTargetSuperClass.isInterface() ? myTargetSuperClass.getExtendsList() : myTargetSuperClass.getImplementsList();
            assert referenceList != null;
            referenceList.add(ref);
          }
        }
        else {
          RefactoringUtil.replaceMovedMemberTypeParameters(aClass, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
          fixReferencesToStatic(aClass, movedMembers);
          final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(aClass);
          myMembersAfterMove.add(movedElement);
          aClass.delete();
        }
      }
    }

    ExplicitSuperDeleter explicitSuperDeleter = new ExplicitSuperDeleter();
    for (PsiMember member : myMembersAfterMove) {
      member.accept(explicitSuperDeleter);
    }
    explicitSuperDeleter.fixSupers();

    final QualifiedThisSuperAdjuster qualifiedThisSuperAdjuster = new QualifiedThisSuperAdjuster();
    for (PsiMember member : myMembersAfterMove) {
      member.accept(qualifiedThisSuperAdjuster);
    }

    ChangeContextUtil.decodeContextInfo(myTargetSuperClass, null, null);

    for (final PsiMember movedMember : myMembersAfterMove) {
      movedMember.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          final PsiExpression qualifierExpression = expression.getQualifierExpression();
          if (qualifierExpression != null) {
            final Boolean preserveQualifier = qualifierExpression.getCopyableUserData(PRESERVE_QUALIFIER);
            if (preserveQualifier != null && !preserveQualifier) {
              qualifierExpression.delete();
              return;
            }
          }
          super.visitReferenceExpression(expression);
        }
      });
      final JavaRefactoringListenerManager listenerManager = JavaRefactoringListenerManager.getInstance(movedMember.getProject());
      ((JavaRefactoringListenerManagerImpl)listenerManager).fireMemberMoved(mySourceClass, movedMember);
    }
  }

  private PsiSubstitutor upDownSuperClassSubstitutor() {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(mySourceClass)) {
      substitutor = substitutor.put(parameter, null);
    }
    final Map<PsiTypeParameter, PsiType> substitutionMap =
      TypeConversionUtil.getSuperClassSubstitutor(myTargetSuperClass, mySourceClass, PsiSubstitutor.EMPTY).getSubstitutionMap();
    for (PsiTypeParameter parameter : substitutionMap.keySet()) {
      final PsiType type = substitutionMap.get(parameter);
      final PsiClass resolvedClass = PsiUtil.resolveClassInType(type);
      if (resolvedClass instanceof PsiTypeParameter) {
        substitutor = substitutor.put((PsiTypeParameter)resolvedClass, JavaPsiFacade.getElementFactory(myProject).createType(parameter));
      }
    }
    return substitutor;
  }

  private static void deleteOverrideAnnotationIfFound(PsiMethod oMethod) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(oMethod, Override.class.getName());
    if (annotation != null) {
      annotation.delete();
    }
  }

  public void moveFieldInitializations() throws IncorrectOperationException {
    LOG.assertTrue(myMembersAfterMove != null);

    final LinkedHashSet<PsiField> movedFields = new LinkedHashSet<PsiField>();
    for (PsiMember member : myMembersAfterMove) {
      if (member instanceof PsiField) {
        movedFields.add((PsiField)member);
      }
    }

    if (movedFields.isEmpty()) return;
    PsiMethod[] constructors = myTargetSuperClass.getConstructors();

    if (constructors.length == 0) {
      constructors = new PsiMethod[]{null};
    }

    HashMap<PsiMethod,HashSet<PsiMethod>> constructorsToSubConstructors = buildConstructorsToSubConstructorsMap(constructors);
    for (PsiMethod constructor : constructors) {
      HashSet<PsiMethod> subConstructors = constructorsToSubConstructors.get(constructor);
      tryToMoveInitializers(constructor, subConstructors, movedFields);
    }
  }

  private static class Initializer {
    public final PsiStatement initializer;
    public final Set<PsiField> movedFieldsUsed;
    public final Set<PsiParameter> usedParameters;
    public final List<PsiElement> statementsToRemove;

    private Initializer(PsiStatement initializer, Set<PsiField> movedFieldsUsed, Set<PsiParameter> usedParameters, List<PsiElement> statementsToRemove) {
      this.initializer = initializer;
      this.movedFieldsUsed = movedFieldsUsed;
      this.statementsToRemove = statementsToRemove;
      this.usedParameters = usedParameters;
    }
  }

  private void tryToMoveInitializers(PsiMethod constructor, HashSet<PsiMethod> subConstructors, LinkedHashSet<PsiField> movedFields) throws IncorrectOperationException {
    final LinkedHashMap<PsiField, Initializer> fieldsToInitializers = new LinkedHashMap<PsiField, Initializer>();
    boolean anyFound = false;

    for (PsiField field : movedFields) {
      PsiStatement commonInitializer = null;
      final ArrayList<PsiElement> fieldInitializersToRemove = new ArrayList<PsiElement>();
      for (PsiMethod subConstructor : subConstructors) {
        commonInitializer = hasCommonInitializer(commonInitializer, subConstructor, field, fieldInitializersToRemove);
        if (commonInitializer == null) break;
      }
      if (commonInitializer != null) {
        final ParametersAndMovedFieldsUsedCollector visitor = new ParametersAndMovedFieldsUsedCollector(movedFields);
        commonInitializer.accept(visitor);
        fieldsToInitializers.put(field, new Initializer(commonInitializer,
                                                        visitor.getUsedFields(), visitor.getUsedParameters(), fieldInitializersToRemove));
        anyFound = true;
      }
    }

    if (!anyFound) return;



    {
      final Set<PsiField> initializedFields = fieldsToInitializers.keySet();
      Set<PsiField> unmovable = RefactoringUtil.transitiveClosure(
              new RefactoringUtil.Graph<PsiField>() {
                public Set<PsiField> getVertices() {
                  return initializedFields;
                }

                public Set<PsiField> getTargets(PsiField source) {
                  return fieldsToInitializers.get(source).movedFieldsUsed;
                }
              },
              new Condition<PsiField>() {
                public boolean value(PsiField object) {
                  return !initializedFields.contains(object);
                }
              }
      );

      for (PsiField psiField : unmovable) {
        fieldsToInitializers.remove(psiField);
      }
    }

    final PsiElementFactory factory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();

    if (constructor == null) {
      constructor = (PsiMethod) myTargetSuperClass.add(factory.createConstructor());
      final String visibilityModifier = VisibilityUtil.getVisibilityModifier(myTargetSuperClass.getModifierList());
      PsiUtil.setModifierProperty(constructor, visibilityModifier, true);
    }


    ArrayList<PsiField> initializedFields = new ArrayList<PsiField>(fieldsToInitializers.keySet());

    Collections.sort(initializedFields, new Comparator<PsiField>() {
      public int compare(PsiField field1, PsiField field2) {
        Initializer i1 = fieldsToInitializers.get(field1);
        Initializer i2 = fieldsToInitializers.get(field2);
        if(i1.movedFieldsUsed.contains(field2)) return 1;
        if(i2.movedFieldsUsed.contains(field1)) return -1;
        return 0;
      }
    });

    for (final PsiField initializedField : initializedFields) {
      Initializer initializer = fieldsToInitializers.get(initializedField);

      //correct constructor parameters and subConstructors super calls
      final PsiParameterList parameterList = constructor.getParameterList();
      for (final PsiParameter parameter : initializer.usedParameters) {
        parameterList.add(parameter);
      }

      for (final PsiMethod subConstructor : subConstructors) {
        modifySuperCall(subConstructor, initializer.usedParameters);
      }

      PsiStatement assignmentStatement = (PsiStatement)constructor.getBody().add(initializer.initializer);

      ChangeContextUtil.decodeContextInfo(assignmentStatement,
                                          myTargetSuperClass, RefactoringUtil.createThisExpression(myManager, null));
      for (PsiElement psiElement : initializer.statementsToRemove) {
        psiElement.delete();
      }
    }
  }

  private static void modifySuperCall(final PsiMethod subConstructor, final Set<PsiParameter> parametersToPassToSuper) {
    final PsiCodeBlock body = subConstructor.getBody();
    if (body != null) {
      PsiMethodCallExpression superCall = null;
      final PsiStatement[] statements = body.getStatements();
      if (statements.length > 0) {
        if (statements[0] instanceof PsiExpressionStatement) {
          final PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
          if (expression instanceof PsiMethodCallExpression) {
            final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
            if ("super".equals(methodCall.getMethodExpression().getText())) {
              superCall = methodCall;
            }
          }
        }
      }

      final PsiElementFactory factory = JavaPsiFacade.getInstance(subConstructor.getProject()).getElementFactory();
      try {
        if (superCall == null) {
            PsiExpressionStatement statement =
              (PsiExpressionStatement)factory.createStatementFromText("super();", null);
            statement = (PsiExpressionStatement)body.addAfter(statement, null);
            superCall = (PsiMethodCallExpression)statement.getExpression();
        }

        final PsiExpressionList argList = superCall.getArgumentList();
        for (final PsiParameter parameter : parametersToPassToSuper) {
          argList.add(factory.createExpressionFromText(parameter.getName(), null));
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  @Nullable
  private PsiStatement hasCommonInitializer(PsiStatement commonInitializer, PsiMethod subConstructor, PsiField field, ArrayList<PsiElement> statementsToRemove) {
    final PsiCodeBlock body = subConstructor.getBody();
    if (body == null) return null;
    final PsiStatement[] statements = body.getStatements();

    // Algorithm: there should be only one write usage of field in a subConstructor,
    // and in that usage field must be a target of top-level assignment, and RHS of assignment
    // should be the same as commonInitializer if latter is non-null.
    //
    // There should be no usages before that initializer, and there should be
    // no write usages afterwards.
    PsiStatement commonInitializerCandidate = null;
    for (PsiStatement statement : statements) {
      final HashSet<PsiStatement> collectedStatements = new HashSet<PsiStatement>();
      collectPsiStatements(statement, collectedStatements);
      boolean doLookup = true;
      for (PsiStatement collectedStatement : collectedStatements) {
        if (collectedStatement instanceof PsiExpressionStatement) {
          final PsiExpression expression = ((PsiExpressionStatement)collectedStatement).getExpression();
          if (expression instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
            final PsiExpression lExpression = assignmentExpression.getLExpression();
            if (lExpression instanceof PsiReferenceExpression) {
              final PsiReferenceExpression lRef = (PsiReferenceExpression)lExpression;
              if (lRef.getQualifierExpression() == null || lRef.getQualifierExpression() instanceof PsiThisExpression) {
                final PsiElement resolved = lRef.resolve();
                if (resolved == field) {
                  doLookup = false;
                  if (commonInitializerCandidate == null) {
                    final PsiExpression initializer = assignmentExpression.getRExpression();
                    if (initializer == null) return null;
                    if (commonInitializer == null) {
                      final IsMovableInitializerVisitor visitor = new IsMovableInitializerVisitor();
                      statement.accept(visitor);
                      if (visitor.isMovable()) {
                        ChangeContextUtil.encodeContextInfo(statement, true);
                        PsiStatement statementCopy = (PsiStatement)statement.copy();
                        ChangeContextUtil.clearContextInfo(statement);
                        statementsToRemove.add(statement);
                        commonInitializerCandidate = statementCopy;
                      }
                      else {
                        return null;
                      }
                    }
                    else {
                      if (PsiEquivalenceUtil.areElementsEquivalent(commonInitializer, statement)) {
                        statementsToRemove.add(statement);
                        commonInitializerCandidate = commonInitializer;
                      }
                      else {
                        return null;
                      }
                    }
                  }
                  else if (!PsiEquivalenceUtil.areElementsEquivalent(commonInitializerCandidate, statement)){
                    return null;
                  }
                }
              }
            }
          }
        }
      }
      if (doLookup) {
        final PsiReference[] references =
          ReferencesSearch.search(field, new LocalSearchScope(statement), false).toArray(new PsiReference[0]);
        if (commonInitializerCandidate == null && references.length > 0) {
          return null;
        }

        for (PsiReference reference : references) {
          if (RefactoringUtil.isAssignmentLHS(reference.getElement())) return null;
        }
      }
    }
    return commonInitializerCandidate;
  }

  private static void collectPsiStatements(PsiElement root, Set<PsiStatement> collected) {
    if (root instanceof PsiStatement){
      collected.add((PsiStatement)root);
    }

    for (PsiElement element : root.getChildren()) {
      collectPsiStatements(element, collected);
    }
  }

  private static class ParametersAndMovedFieldsUsedCollector extends JavaRecursiveElementWalkingVisitor {
    private final Set<PsiField> myMovedFields;
    private final Set<PsiField> myUsedFields;

    private final Set<PsiParameter> myUsedParameters = new LinkedHashSet<PsiParameter>();

    private ParametersAndMovedFieldsUsedCollector(HashSet<PsiField> movedFields) {
      myMovedFields = movedFields;
      myUsedFields = new HashSet<PsiField>();
    }

    public Set<PsiParameter> getUsedParameters() {
      return myUsedParameters;
    }

    public Set<PsiField> getUsedFields() {
      return myUsedFields;
    }

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      final PsiExpression qualifierExpression = expression.getQualifierExpression();
      if (qualifierExpression != null
              && !(qualifierExpression instanceof PsiThisExpression)) {
        return;
      }
      final PsiElement resolved = expression.resolve();
      if (resolved instanceof PsiParameter) {
        myUsedParameters.add((PsiParameter)resolved);
      } else if (myMovedFields.contains(resolved)) {
        myUsedFields.add((PsiField)resolved);
      }
    }
  }

  private class IsMovableInitializerVisitor extends JavaRecursiveElementWalkingVisitor {
    private boolean myIsMovable = true;

    public boolean isMovable() {
      return myIsMovable;
    }

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    @Override public void visitReferenceElement(PsiJavaCodeReferenceElement referenceElement) {
      if (!myIsMovable) return;
      final PsiExpression qualifier;
      if (referenceElement instanceof PsiReferenceExpression) {
        qualifier = ((PsiReferenceExpression) referenceElement).getQualifierExpression();
      } else {
        qualifier = null;
      }
      if (qualifier == null || qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
        final PsiElement resolved = referenceElement.resolve();
        if (!(resolved instanceof PsiParameter)) {
          if (resolved instanceof PsiClass && (((PsiClass) resolved).hasModifierProperty(PsiModifier.STATIC) || ((PsiClass)resolved).getContainingClass() == null)) {
            return;
          }
          PsiClass containingClass = null;
          if (resolved instanceof PsiMember && !((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC)) {
            containingClass = ((PsiMember) resolved).getContainingClass();
          }
          myIsMovable = containingClass != null && InheritanceUtil.isInheritorOrSelf(myTargetSuperClass, containingClass, true);
        }
      } else {
        qualifier.accept(this);
      }
    }

    @Override public void visitElement(PsiElement element) {
      if (myIsMovable) {
        super.visitElement(element);
      }
    }
  }

  private HashMap<PsiMethod,HashSet<PsiMethod>> buildConstructorsToSubConstructorsMap(final PsiMethod[] constructors) {
    final HashMap<PsiMethod,HashSet<PsiMethod>> constructorsToSubConstructors = new HashMap<PsiMethod, HashSet<PsiMethod>>();
    for (PsiMethod constructor : constructors) {
      final HashSet<PsiMethod> referencingSubConstructors = new HashSet<PsiMethod>();
      constructorsToSubConstructors.put(constructor, referencingSubConstructors);
      if (constructor != null) {
        // find references
        for (PsiReference reference : ReferencesSearch.search(constructor, new LocalSearchScope(mySourceClass), false)) {
          final PsiElement element = reference.getElement();
          if (element != null && "super".equals(element.getText())) {
            PsiMethod parentMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            if (parentMethod != null && parentMethod.isConstructor()) {
              referencingSubConstructors.add(parentMethod);
            }
          }
        }
      }

      // check default constructor
      if (constructor == null || constructor.getParameterList().getParametersCount() == 0) {
        RefactoringUtil.visitImplicitSuperConstructorUsages(mySourceClass, new RefactoringUtil.ImplicitConstructorUsageVisitor() {
          public void visitConstructor(PsiMethod constructor, PsiMethod baseConstructor) {
            referencingSubConstructors.add(constructor);
          }

          public void visitClassWithoutConstructors(PsiClass aClass) {
          }
        }, myTargetSuperClass);

      }
    }
    return constructorsToSubConstructors;
  }

  private void fixReferencesToStatic(PsiElement classMember, Set<PsiMember> movedMembers) throws IncorrectOperationException {
    final StaticReferencesCollector collector = new StaticReferencesCollector(movedMembers);
    classMember.accept(collector);
    ArrayList<PsiJavaCodeReferenceElement> refs = collector.getReferences();
    ArrayList<PsiElement> members = collector.getReferees();
    ArrayList<PsiClass> classes = collector.getRefereeClasses();
    PsiElementFactory factory = JavaPsiFacade.getInstance(classMember.getProject()).getElementFactory();

    for (int i = 0; i < refs.size(); i++) {
      PsiJavaCodeReferenceElement ref = refs.get(i);
      PsiElement namedElement = members.get(i);
      PsiClass aClass = classes.get(i);

      if (namedElement instanceof PsiNamedElement) {
        PsiReferenceExpression newRef =
                (PsiReferenceExpression) factory.createExpressionFromText
                ("a." + ((PsiNamedElement) namedElement).getName(),
                        null);
        PsiExpression qualifierExpression = newRef.getQualifierExpression();
        assert qualifierExpression != null;
        qualifierExpression = (PsiExpression)qualifierExpression.replace(factory.createReferenceExpression(aClass));
        qualifierExpression.putCopyableUserData(PRESERVE_QUALIFIER, ref.isQualified());
        ref.replace(newRef);
      }
    }
  }

  private class StaticReferencesCollector extends ClassMemberReferencesVisitor {
    private ArrayList<PsiJavaCodeReferenceElement> myReferences;
    private ArrayList<PsiElement> myReferees;
    private ArrayList<PsiClass> myRefereeClasses;
    private final Set<PsiMember> myMovedMembers;

    private StaticReferencesCollector(Set<PsiMember> movedMembers) {
      super(mySourceClass);
      myMovedMembers = movedMembers;
      myReferees = new ArrayList<PsiElement>();
      myRefereeClasses = new ArrayList<PsiClass>();
      myReferences = new ArrayList<PsiJavaCodeReferenceElement>();
    }

    public ArrayList<PsiElement> getReferees() {
      return myReferees;
    }

    public ArrayList<PsiClass> getRefereeClasses() {
      return myRefereeClasses;
    }

    public ArrayList<PsiJavaCodeReferenceElement> getReferences() {
      return myReferences;
    }

    protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
      if (classMember.hasModifierProperty(PsiModifier.STATIC)) {
        if (!myMovedMembers.contains(classMember) &&
            RefactoringHierarchyUtil.isMemberBetween(myTargetSuperClass, mySourceClass, classMember)) {
          myReferences.add(classMemberReference);
          myReferees.add(classMember);
          myRefereeClasses.add(classMember.getContainingClass());
        }
        else if (myMovedMembers.contains(classMember) || myMembersAfterMove.contains(classMember)) {
          myReferences.add(classMemberReference);
          myReferees.add(classMember);
          myRefereeClasses.add(myTargetSuperClass);
        }
      }
    }
  }

  private class QualifiedThisSuperAdjuster extends JavaRecursiveElementVisitor {
    @Override public void visitThisExpression(PsiThisExpression expression) {
      super.visitThisExpression(expression);
      final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
      if (qualifier != null && qualifier.isReferenceTo(mySourceClass)) {
        try {
          qualifier.bindToElement(myTargetSuperClass);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }

    @Override public void visitSuperExpression(PsiSuperExpression expression) {
      super.visitSuperExpression(expression);
      final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
      if (qualifier != null && qualifier.isReferenceTo(mySourceClass)) {
        try {
          expression.replace(JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory().createExpressionFromText(myTargetSuperClass.getName() + ".this", null));
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }

  private class ExplicitSuperDeleter extends JavaRecursiveElementWalkingVisitor {
    private final ArrayList<PsiExpression> mySupersToDelete = new ArrayList<PsiExpression>();
    private final ArrayList<PsiSuperExpression> mySupersToChangeToThis = new ArrayList<PsiSuperExpression>();

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      if(expression.getQualifierExpression() instanceof PsiSuperExpression) {
        PsiElement resolved = expression.resolve();
        if (resolved == null || resolved instanceof PsiMethod && shouldFixSuper((PsiMethod) resolved)) {
          mySupersToDelete.add(expression.getQualifierExpression());
        }
      }
    }

    @Override public void visitSuperExpression(PsiSuperExpression expression) {
      mySupersToChangeToThis.add(expression);
    }

    @Override public void visitClass(PsiClass aClass) {
      // do nothing
    }

    private boolean shouldFixSuper(PsiMethod method) {
      for (PsiMember element : myMembersAfterMove) {
        if (element instanceof PsiMethod) {
          PsiMethod member = (PsiMethod)element;
          // if there is such member among moved members, super qualifier
          // should not be removed
          final PsiManager manager = method.getManager();
          if (manager.areElementsEquivalent(member.getContainingClass(), method.getContainingClass()) &&
              MethodSignatureUtil.areSignaturesEqual(member, method)) {
            return false;
          }
        }
      }

      final PsiMethod methodFromSuper = myTargetSuperClass.findMethodBySignature(method, false);
      return methodFromSuper == null;
    }

    public void fixSupers() throws IncorrectOperationException {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
      PsiThisExpression thisExpression = (PsiThisExpression) factory.createExpressionFromText("this", null);
      for (PsiExpression psiExpression : mySupersToDelete) {
        psiExpression.delete();
      }

      for (PsiSuperExpression psiSuperExpression : mySupersToChangeToThis) {
        psiSuperExpression.replace(thisExpression);
      }
    }
  }

  private static boolean willBeUsedInSubclass(PsiElement member, Set<PsiMember> movedMembers, PsiClass superclass, PsiClass subclass) {
    for (PsiReference ref : ReferencesSearch.search(member, new LocalSearchScope(subclass), false)) {
      PsiElement element = ref.getElement();
      if (!RefactoringHierarchyUtil.willBeInTargetClass(element, movedMembers, superclass, false)) {
        return true;
      }
    }
    return false;
  }

  public static boolean checkedInterfacesContain(Collection<MemberInfo> memberInfos, PsiMethod psiMethod) {
    for (MemberInfo memberInfo : memberInfos) {
      if (memberInfo.isChecked() &&
          memberInfo.getMember() instanceof PsiClass &&
          Boolean.FALSE.equals(memberInfo.getOverrides())) {
        if (((PsiClass)memberInfo.getMember()).findMethodBySignature(psiMethod, true) != null) {
          return true;
        }
      }
    }
    return false;
  }

  private class PullUpUsageViewDescriptor implements UsageViewDescriptor {
    public String getProcessedElementsHeader() {
      return "Pull up members from";
    }

    @NotNull
    public PsiElement[] getElements() {
      return new PsiElement[]{mySourceClass};
    }

    public String getCodeReferencesText(int usagesCount, int filesCount) {
      return "Class to pull up members to \"" + RefactoringUIUtil.getDescription(myTargetSuperClass, true) + "\"";
    }

    public String getCommentReferencesText(int usagesCount, int filesCount) {
      return null;
    }
  }
}
