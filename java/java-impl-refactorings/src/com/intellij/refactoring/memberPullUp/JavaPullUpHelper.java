// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.memberPullUp;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaPullUpHelper implements PullUpHelper<MemberInfo> {
  private static final Logger LOG = Logger.getInstance(JavaPullUpHelper.class);

  private static final Key<Boolean> PRESERVE_QUALIFIER = Key.create("PRESERVE_QUALIFIER");


  private final PsiClass mySourceClass;
  private final PsiClass myTargetSuperClass;
  private final boolean myIsTargetInterface;
  private final DocCommentPolicy myJavaDocPolicy;
  private final Set<PsiMember> myMembersAfterMove;
  private final Set<PsiMember> myMembersToMove;
  private final Project myProject;

  private final QualifiedThisSuperAdjuster myThisSuperAdjuster;
  private final ExplicitSuperDeleter myExplicitSuperDeleter;

  public JavaPullUpHelper(PullUpData data) {
    myProject = data.getProject();
    myMembersToMove = data.getMembersToMove();
    myMembersAfterMove = data.getMovedMembers();
    myTargetSuperClass = data.getTargetClass();
    mySourceClass = data.getSourceClass();
    myJavaDocPolicy = data.getDocCommentPolicy();
    myIsTargetInterface = myTargetSuperClass.isInterface();

    myThisSuperAdjuster = new QualifiedThisSuperAdjuster();
    myExplicitSuperDeleter = new ExplicitSuperDeleter();
  }

  @Override
  public void encodeContextInfo(MemberInfo info) {
    ChangeContextUtil.encodeContextInfo(info.getMember(), true);
  }

  @Override
  public void move(MemberInfo info, PsiSubstitutor substitutor) {
    if (info.getMember() instanceof PsiMethod) {
      doMoveMethod(substitutor, info);
    }
    else if (info.getMember() instanceof PsiField) {
      doMoveField(substitutor, info);
    }
    else if (info.getMember() instanceof PsiClass) {
      doMoveClass(substitutor, info);
    }
    else if (info.getMember() instanceof PsiClassInitializer initializer) {
      PsiClassInitializer copy = (PsiClassInitializer)initializer.copy();
      final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(copy);
      myMembersAfterMove.add(movedElement);
      initializer.delete();
    }
  }

  @Override
  public void postProcessMember(PsiMember member) {
    member.accept(myExplicitSuperDeleter);
    member.accept(myThisSuperAdjuster);

    ChangeContextUtil.decodeContextInfo(member, null, null);

    member.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
        final PsiElement qualifierExpression = reference.getQualifier();
        if (qualifierExpression != null) {
          final Boolean preserveQualifier = qualifierExpression.getCopyableUserData(PRESERVE_QUALIFIER);
          if (preserveQualifier != null && !preserveQualifier) {
            PsiElement target = reference.resolve();
            if (target != null) {
              PsiJavaCodeReferenceElement copy = (PsiJavaCodeReferenceElement)reference.copy();
              Objects.requireNonNull(copy.getQualifier()).delete();
              if (copy.resolve() == target) {
                qualifierExpression.delete();
                return;
              }
            }
          }
        }
        super.visitReferenceElement(reference);
      }
    });

  }

  @Override
  public void setCorrectVisibility(MemberInfo info) {
    PsiModifierListOwner modifierListOwner = info.getMember();
    if (myIsTargetInterface) {
      PsiUtil.setModifierProperty(modifierListOwner, PsiModifier.PUBLIC, true);
    }
    else if (modifierListOwner.hasModifierProperty(PsiModifier.PRIVATE)) {
      if (info.isToAbstract() || willBeUsedInSubclass(modifierListOwner, myTargetSuperClass, mySourceClass)) {
        PsiUtil.setModifierProperty(modifierListOwner, PsiModifier.PROTECTED, true);
      }
      if (modifierListOwner instanceof PsiClass) {
        modifierListOwner.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitMethod(@NotNull PsiMethod method) {
            check(method);
          }

          @Override
          public void visitField(@NotNull PsiField field) {
            check(field);
          }

          @Override
          public void visitClass(@NotNull PsiClass aClass) {
            check(aClass);
            super.visitClass(aClass);
          }

          private void check(PsiMember member) {
            if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
              if (willBeUsedInSubclass(member, myTargetSuperClass, mySourceClass)) {
                PsiUtil.setModifierProperty(member, PsiModifier.PROTECTED, true);
              }
            }
          }
        });
      }
    }
  }

  private void doMoveClass(PsiSubstitutor substitutor, MemberInfo info) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    PsiClass aClass = (PsiClass)info.getMember();
    if (Boolean.FALSE.equals(info.getOverrides())) {
      final PsiReferenceList sourceReferenceList = info.getSourceReferenceList();
      PsiJavaCodeReferenceElement ref = sourceReferenceList == null ? null :
                                        mySourceClass.equals(sourceReferenceList.getParent()) ?
                                          RefactoringUtil.removeFromReferenceList(sourceReferenceList, aClass) :
                                          RefactoringUtil.findReferenceToClass(sourceReferenceList, aClass);
      if (ref != null && !myTargetSuperClass.isInheritor(aClass, false)) {
        RefactoringUtil.replaceMovedMemberTypeParameters(ref, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
        final PsiReferenceList referenceList =
          myIsTargetInterface ? myTargetSuperClass.getExtendsList() : myTargetSuperClass.getImplementsList();
        assert referenceList != null;
        referenceList.add(ref);
      }
    }
    else {
      PsiClass copy = (PsiClass)aClass.copy();
      RefactoringUtil.renameConflictingTypeParameters(copy, myTargetSuperClass);
      RefactoringUtil.replaceMovedMemberTypeParameters(copy, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
      fixReferencesToStatic(copy);
      final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(copy);
      myMembersAfterMove.add(movedElement);
      aClass.delete();
    }
  }

  private void doMoveField(PsiSubstitutor substitutor, MemberInfo info) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    PsiField field = (PsiField)info.getMember();
    field.normalizeDeclaration();
    RefactoringUtil.replaceMovedMemberTypeParameters(field, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
    fixReferencesToStatic(field);
    if (myIsTargetInterface) {
      PsiUtil.setModifierProperty(field, PsiModifier.PUBLIC, true);
    }
    final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(convertFieldToLanguage(field, myTargetSuperClass.getLanguage()));
    myMembersAfterMove.add(movedElement);
    field.delete();
  }

  private void doMoveMethod(PsiSubstitutor substitutor, MemberInfo info) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    PsiMethod method = (PsiMethod)info.getMember();
    PsiMethod sibling = method;
    PsiMethod anchor = null;
    while (sibling != null) {
      sibling = PsiTreeUtil.getNextSiblingOfType(sibling, PsiMethod.class);
      if (sibling != null) {
        anchor = MethodSignatureUtil
          .findMethodInSuperClassBySignatureInDerived(method.getContainingClass(), myTargetSuperClass,
                                                      sibling.getSignature(PsiSubstitutor.EMPTY), false);
        if (anchor != null) {
          break;
        }
      }
    }
    PsiMethod methodCopy = (PsiMethod)method.copy();
    RefactoringUtil.renameConflictingTypeParameters(methodCopy, myTargetSuperClass);
    RefactoringUtil.replaceMovedMemberTypeParameters(methodCopy, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);

    Language language = myTargetSuperClass.getLanguage();
    final PsiMethod superClassMethod = MethodSignatureUtil.findMethodBySuperSignature(myTargetSuperClass, method.getSignature(substitutor), false);
    if (superClassMethod != null && superClassMethod.findDeepestSuperMethods().length == 0 ||
        method.findSuperMethods(myTargetSuperClass).length == 0) {
      deleteOverrideAnnotationIfFound(methodCopy);
    }
    boolean isOriginalMethodAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
    if (myIsTargetInterface || info.isToAbstract()) {
      ChangeContextUtil.clearContextInfo(method);

      if (!info.isToAbstract() && !method.hasModifierProperty(PsiModifier.ABSTRACT) && PsiUtil.isLanguageLevel8OrHigher(myTargetSuperClass)) {
        //pull as default
        RefactoringUtil.makeMethodDefault(methodCopy);
        isOriginalMethodAbstract = true;
      }
      else {
        if (info.isToAbstract() && method.hasModifierProperty(PsiModifier.DEFAULT)) {
          PsiUtil.setModifierProperty(methodCopy, PsiModifier.DEFAULT, false);
        }
        RefactoringUtil.makeMethodAbstract(myTargetSuperClass, methodCopy);
      }

      myJavaDocPolicy.processCopiedJavaDoc(methodCopy.getDocComment(), method.getDocComment(), isOriginalMethodAbstract);

      final PsiMethod movedElement;
      if (superClassMethod != null && superClassMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        movedElement = (PsiMethod)superClassMethod.replace(convertMethodToLanguage(methodCopy, language));
      }
      else {
        movedElement =
          (PsiMethod)(anchor != null ? myTargetSuperClass.addBefore(methodCopy, anchor) : myTargetSuperClass.add(methodCopy));
      }
      OverrideImplementUtil.annotateOnOverrideImplement(method, mySourceClass, movedElement);
      if (!PsiUtil.isLanguageLevel6OrHigher(mySourceClass) && myIsTargetInterface) {
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
      RefactoringUtil.replaceMovedMemberTypeParameters(methodCopy, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
      fixReferencesToStatic(methodCopy);

      if (superClassMethod != null && superClassMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        superClassMethod.replace(convertMethodToLanguage(methodCopy, language));
      }
      else {
        final PsiMember movedElement =
          anchor != null ? (PsiMember)myTargetSuperClass.addBefore(convertMethodToLanguage(methodCopy,
                                                                                           language), anchor) : (PsiMember)myTargetSuperClass.add(
            convertMethodToLanguage(
              methodCopy, language));
        myMembersAfterMove.add(movedElement);
      }
      method.delete();
    }
  }

  private static PsiMethod convertMethodToLanguage(PsiMethod method, Language language) {
    if (method.getLanguage().equals(language)) {
      return method;
    }
    return JVMElementFactories.getFactory(language, method.getProject()).createMethodFromText(method.getText(), null);
  }

  private static PsiField convertFieldToLanguage(PsiField field, Language language) {
    if (field.getLanguage().equals(language)) {
      return field;
    }
    return JVMElementFactories.getFactory(language, field.getProject()).createField(field.getName(), field.getType());
  }

  private static void deleteOverrideAnnotationIfFound(PsiMethod oMethod) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(oMethod, Override.class.getName());
    if (annotation != null) {
      annotation.delete();
    }
  }

  @Override
  public void moveFieldInitializations(LinkedHashSet<PsiField> movedFields) {
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

  @Override
  public void updateUsage(PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      PsiExpression qualifierExpression = ((PsiReferenceExpression)element).getQualifierExpression();
      if (qualifierExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifierExpression).resolve() == mySourceClass) {
        ((PsiReferenceExpression)qualifierExpression).bindToElement(myTargetSuperClass);
      }
      else if (qualifierExpression == null && myTargetSuperClass.isInterface()) {
        ((PsiReferenceExpression)element).setQualifierExpression(JavaPsiFacade.getElementFactory(myProject).createReferenceExpression(myTargetSuperClass));
      }
    }
  }

  private static final class Initializer {
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
    final LinkedHashMap<PsiField, Initializer> fieldsToInitializers = new LinkedHashMap<>();
    boolean anyFound = false;

    for (PsiField field : movedFields) {
      PsiStatement commonInitializer = null;
      final ArrayList<PsiElement> fieldInitializersToRemove = new ArrayList<>();
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
        new RefactoringUtil.Graph<>() {
          @Override
          public Set<PsiField> getVertices() {
            return initializedFields;
          }

          @Override
          public Set<PsiField> getTargets(PsiField source) {
            return fieldsToInitializers.get(source).movedFieldsUsed;
          }
        },
              object -> !initializedFields.contains(object)
      );

      for (PsiField psiField : unmovable) {
        fieldsToInitializers.remove(psiField);
      }
    }

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);

    if (constructor == null) {
      constructor = (PsiMethod) myTargetSuperClass.add(factory.createConstructor());
      final String visibilityModifier = VisibilityUtil.getVisibilityModifier(myTargetSuperClass.getModifierList());
      PsiUtil.setModifierProperty(constructor, visibilityModifier, true);
    }


    ArrayList<PsiField> initializedFields = new ArrayList<>(fieldsToInitializers.keySet());

    initializedFields.sort((field1, field2) -> {
      Initializer i1 = fieldsToInitializers.get(field1);
      Initializer i2 = fieldsToInitializers.get(field2);
      if (i1.movedFieldsUsed.contains(field2)) return 1;
      if (i2.movedFieldsUsed.contains(field1)) return -1;
      if (i1.usedParameters.stream().anyMatch(p -> p.isVarArgs())) return 1;
      if (i2.usedParameters.stream().anyMatch(p -> p.isVarArgs())) return -1;

      return i1.movedFieldsUsed.size() - i2.movedFieldsUsed.size();
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

      PsiManager manager = PsiManager.getInstance(myProject);
      ChangeContextUtil.decodeContextInfo(assignmentStatement, myTargetSuperClass, RefactoringChangeUtil.createThisExpression(manager, null));
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
      if (statements.length > 0 && statements[0] instanceof PsiExpressionStatement) {
        final PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
        if (expression instanceof PsiMethodCallExpression methodCall && "super".equals(methodCall.getMethodExpression().getText())) {
          superCall = methodCall;
        }
      }

      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(subConstructor.getProject());
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
  private PsiStatement hasCommonInitializer(PsiStatement commonInitializer, PsiMethod subConstructor, PsiField field, ArrayList<? super PsiElement> statementsToRemove) {
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
      final HashSet<PsiStatement> collectedStatements = new HashSet<>();
      collectPsiStatements(statement, collectedStatements);
      boolean doLookup = true;
      for (PsiStatement collectedStatement : collectedStatements) {
        if (collectedStatement instanceof PsiExpressionStatement) {
          final PsiExpression expression = ((PsiExpressionStatement)collectedStatement).getExpression();
          if (expression instanceof PsiAssignmentExpression assignmentExpression &&
              assignmentExpression.getLExpression() instanceof PsiReferenceExpression lRef &&
              (lRef.getQualifierExpression() == null || lRef.getQualifierExpression() instanceof PsiThisExpression) &&
              lRef.resolve() == field) {
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
            else if (!PsiEquivalenceUtil.areElementsEquivalent(commonInitializerCandidate, statement)) {
              return null;
            }
          }
        }
      }
      if (doLookup) {
        final PsiReference[] references =
          ReferencesSearch.search(field, new LocalSearchScope(statement), false).toArray(PsiReference.EMPTY_ARRAY);
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

  private static void collectPsiStatements(PsiElement root, Set<? super PsiStatement> collected) {
    SyntaxTraverser.psiTraverser(root).filter(PsiStatement.class).addAllTo(collected);
  }

  private static final class ParametersAndMovedFieldsUsedCollector extends JavaRecursiveElementWalkingVisitor {
    private final Set<PsiField> myMovedFields;
    private final Set<PsiField> myUsedFields;

    private final Set<PsiParameter> myUsedParameters = new LinkedHashSet<>();

    private ParametersAndMovedFieldsUsedCollector(HashSet<PsiField> movedFields) {
      myMovedFields = movedFields;
      myUsedFields = new HashSet<>();
    }

    public Set<PsiParameter> getUsedParameters() {
      return myUsedParameters;
    }

    public Set<PsiField> getUsedFields() {
      return myUsedFields;
    }

    @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
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

    @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    @Override public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement referenceElement) {
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

    @Override public void visitElement(@NotNull PsiElement element) {
      if (myIsMovable) {
        super.visitElement(element);
      }
    }
  }

  private HashMap<PsiMethod,HashSet<PsiMethod>> buildConstructorsToSubConstructorsMap(final PsiMethod[] constructors) {
    final HashMap<PsiMethod,HashSet<PsiMethod>> constructorsToSubConstructors = new HashMap<>();
    for (PsiMethod constructor : constructors) {
      final HashSet<PsiMethod> referencingSubConstructors = new HashSet<>();
      constructorsToSubConstructors.put(constructor, referencingSubConstructors);
      if (constructor != null) {
        // find references
        for (PsiReference reference : ReferencesSearch.search(constructor, new LocalSearchScope(mySourceClass), false)) {
          final PsiElement element = reference.getElement();
          if ("super".equals(element.getText())) {
            PsiMethod parentMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            if (parentMethod != null && parentMethod.isConstructor()) {
              referencingSubConstructors.add(parentMethod);
            }
          }
        }
      }

      // check default constructor
      if (constructor == null || constructor.getParameterList().isEmpty()) {
        RefactoringUtil.visitImplicitSuperConstructorUsages(mySourceClass, new RefactoringUtil.ImplicitConstructorUsageVisitor() {
          @Override
          public void visitConstructor(PsiMethod constructor, PsiMethod baseConstructor) {
            referencingSubConstructors.add(constructor);
          }

          @Override
          public void visitClassWithoutConstructors(PsiClass aClass) {
          }
        }, myTargetSuperClass);

      }
    }
    return constructorsToSubConstructors;
  }

  private void fixReferencesToStatic(PsiElement classMember) throws IncorrectOperationException {
    final StaticReferencesCollector collector = new StaticReferencesCollector();
    classMember.accept(collector);
    ArrayList<PsiJavaCodeReferenceElement> refs = collector.getReferences();
    ArrayList<PsiElement> members = collector.getReferees();
    ArrayList<PsiClass> classes = collector.getRefereeClasses();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(classMember.getProject());

    for (int i = 0; i < refs.size(); i++) {
      PsiJavaCodeReferenceElement ref = refs.get(i);
      PsiElement namedElement = members.get(i);
      PsiClass aClass = classes.get(i);

      if (namedElement instanceof PsiNamedElement) {
        PsiElement oldQualifier = ref.getQualifier();
        if (oldQualifier != null) {
          oldQualifier.delete();
        }
        String template = aClass.getQualifiedName() + "." + ref.getText();
        PsiJavaCodeReferenceElement newRef = ref instanceof PsiReferenceExpression ?
                                             (PsiReferenceExpression)factory.createExpressionFromText(template, null) :
                                             factory.createReferenceFromText(template, null);
        Objects.requireNonNull(newRef.getQualifier()).putCopyableUserData(PRESERVE_QUALIFIER, oldQualifier != null);
        ref.replace(newRef);
      }
    }
  }

  private final class StaticReferencesCollector extends ClassMemberReferencesVisitor {
    private final ArrayList<PsiJavaCodeReferenceElement> myReferences;
    private final ArrayList<PsiElement> myReferees;
    private final ArrayList<PsiClass> myRefereeClasses;

    private StaticReferencesCollector() {
      super(mySourceClass);
      myReferees = new ArrayList<>();
      myRefereeClasses = new ArrayList<>();
      myReferences = new ArrayList<>();
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

    @Override
    protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
      if (classMember.hasModifierProperty(PsiModifier.STATIC)) {
        if (!myMembersToMove.contains(classMember) &&
            RefactoringHierarchyUtil.isMemberBetween(myTargetSuperClass, mySourceClass, classMember)) {
          myReferences.add(classMemberReference);
          myReferees.add(classMember);
          myRefereeClasses.add(classMember.getContainingClass());
        }
        else if (myMembersToMove.contains(classMember) || myMembersAfterMove.contains(classMember)) {
          myReferences.add(classMemberReference);
          myReferees.add(classMember);
          myRefereeClasses.add(myTargetSuperClass);
        }
      }
    }
  }

  private class QualifiedThisSuperAdjuster extends JavaRecursiveElementVisitor {
    @Override public void visitThisExpression(@NotNull PsiThisExpression expression) {
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

    @Override public void visitSuperExpression(@NotNull PsiSuperExpression expression) {
      super.visitSuperExpression(expression);
      final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
      if (qualifier != null && qualifier.isReferenceTo(mySourceClass)) {
        try {
          expression.replace(JavaPsiFacade.getElementFactory(myProject).createExpressionFromText(myTargetSuperClass.getName() + ".this", null));
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }

  private class ExplicitSuperDeleter extends JavaRecursiveElementWalkingVisitor {
    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if(expression.getQualifierExpression() instanceof PsiSuperExpression) {
        PsiElement resolved = expression.resolve();
        if (resolved == null || resolved instanceof PsiMethod && shouldFixSuper((PsiMethod) resolved)) {
          expression.getQualifierExpression().delete();
        }
      }
    }


    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // do nothing
    }

    private boolean shouldFixSuper(PsiMethod method) {
      for (PsiMember element : myMembersAfterMove) {
        if (element instanceof PsiMethod member) {
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
  }

  private boolean willBeUsedInSubclass(PsiElement member, PsiClass superclass, PsiClass subclass) {
    for (PsiReference ref : ReferencesSearch.search(member, new LocalSearchScope(subclass), false)) {
      PsiElement element = ref.getElement();
      if (!RefactoringHierarchyUtil.willBeInTargetClass(element, myMembersToMove, superclass, false)) {
        return true;
      }
    }
    return false;
  }
}
