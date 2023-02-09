// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ConflictsDialogBase;
import com.intellij.refactoring.inheritanceToDelegation.usageInfo.*;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.refactoring.util.classRefs.ClassInstanceScanner;
import com.intellij.refactoring.util.classRefs.ClassReferenceScanner;
import com.intellij.refactoring.util.classRefs.ClassReferenceSearchingScanner;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usages.UsageInfoToUsageConverter;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.SealedUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InheritanceToDelegationProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(InheritanceToDelegationProcessor.class);
  private final PsiClass myClass;
  private final String myInnerClassName;
  private final boolean myIsDelegateOtherMembers;
  private final Set<PsiClass> myDelegatedInterfaces;
  private final Set<PsiMethod> myDelegatedMethods;
  private final HashMap<PsiMethod,String> myDelegatedMethodsVisibility;
  private final Set<PsiMethod> myOverriddenMethods;

  private final PsiClass myBaseClass;
  private final Set<PsiMember> myBaseClassMembers;
  private final String myFieldName;
  private final String myGetterName;
  private final boolean myGenerateGetter;
  private final Set<PsiClass> myBaseClassBases;
  private Set<PsiClass> myClassImplementedInterfaces;
  private final PsiElementFactory myFactory;
  private final PsiClassType myBaseClassType;
  private final PsiManager myManager;
  private final boolean myIsInnerClassNeeded;
  private Set<PsiClass> myClassInheritors;
  private HashSet<PsiMethod> myAbstractDelegatedMethods;
  private final Map<PsiClass, PsiSubstitutor> mySuperClassesToSubstitutors = new HashMap<>();


  public InheritanceToDelegationProcessor(Project project,
                                          PsiClass aClass,
                                          @NotNull PsiClass targetBaseClass,
                                          String fieldName,
                                          String innerClassName,
                                          PsiClass[] delegatedInterfaces,
                                          PsiMethod[] delegatedMethods,
                                          boolean delegateOtherMembers,
                                          boolean generateGetter) {
    super(project);

    myClass = aClass;
    myInnerClassName = innerClassName;
    myIsDelegateOtherMembers = delegateOtherMembers;
    myManager = myClass.getManager();
    myFactory = JavaPsiFacade.getElementFactory(myManager.getProject());

    myBaseClass = targetBaseClass;
    LOG.assertTrue(
             // && !myBaseClass.isInterface()
            myBaseClass.getQualifiedName() == null || !myBaseClass.getQualifiedName().equals(CommonClassNames.JAVA_LANG_OBJECT), myBaseClass);
    myBaseClassMembers = getAllBaseClassMembers();
    myBaseClassBases = getAllBases();
    myBaseClassType = myFactory.createType(myBaseClass, getSuperSubstitutor (myBaseClass));

    myIsInnerClassNeeded = InheritanceToDelegationUtil.isInnerClassNeeded(myClass, myBaseClass);


    myFieldName = fieldName;
    final String propertyName = JavaCodeStyleManager.getInstance(myProject).variableNameToPropertyName(myFieldName, VariableKind.FIELD);
    myGetterName = GenerateMembersUtil.suggestGetterName(propertyName, myBaseClassType, myProject);
    myGenerateGetter = generateGetter;

    myDelegatedInterfaces = new LinkedHashSet<>();
    Collections.addAll(myDelegatedInterfaces, delegatedInterfaces);
    myDelegatedMethods = new LinkedHashSet<>();
    Collections.addAll(myDelegatedMethods, delegatedMethods);
    myDelegatedMethodsVisibility = new HashMap<>();
    for (PsiMethod method : myDelegatedMethods) {
      MethodSignature signature = method.getSignature(getSuperSubstitutor(method.getContainingClass()));
      PsiMethod overridingMethod = MethodSignatureUtil.findMethodBySignature(myClass, signature, false);
      if (overridingMethod != null) {
        myDelegatedMethodsVisibility.put(method,
                                         VisibilityUtil.getVisibilityModifier(overridingMethod.getModifierList()));
      }
    }

    myOverriddenMethods = getOverriddenMethods();
  }

  private PsiSubstitutor getSuperSubstitutor(final PsiClass superClass) {
    PsiSubstitutor result = mySuperClassesToSubstitutors.get(superClass);
    if (result == null) {
      result = TypeConversionUtil.getSuperClassSubstitutor(superClass, myClass, PsiSubstitutor.EMPTY);
      mySuperClassesToSubstitutors.put(superClass, result);
    }
    return result;
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new InheritanceToDelegationViewDescriptor(myClass);
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    ArrayList<UsageInfo> usages = new ArrayList<>();
    final PsiClass[] inheritors = ClassInheritorsSearch.search(myClass).toArray(PsiClass.EMPTY_ARRAY);
    myClassInheritors = new HashSet<>();
    myClassInheritors.add(myClass);
    Collections.addAll(myClassInheritors, inheritors);

    {
      ClassReferenceScanner scanner = new ClassReferenceSearchingScanner(myClass);
      final MyClassInstanceReferenceVisitor instanceReferenceVisitor = new MyClassInstanceReferenceVisitor(myClass, usages);
      scanner.processReferences(new ClassInstanceScanner(myClass, instanceReferenceVisitor));

      MyClassMemberReferencesVisitor visitor = new MyClassMemberReferencesVisitor(usages, instanceReferenceVisitor);
      myClass.accept(visitor);

      myClassImplementedInterfaces = instanceReferenceVisitor.getImplementedInterfaces();
    }
    for (PsiClass inheritor : inheritors) {
      processClass(inheritor, usages);
    }

    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  private FieldAccessibility getFieldAccessibility(PsiElement element) {
    for (PsiClass aClass : myClassInheritors) {
      if (PsiTreeUtil.isAncestor(aClass, element, false)) {
        return new FieldAccessibility(true, aClass);
      }
    }
    return FieldAccessibility.INVISIBLE;
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final UsageInfo[] usagesIn = refUsages.get();
    ArrayList<UsageInfo> oldUsages = new ArrayList<>();
    Collections.addAll(oldUsages, usagesIn);
    final ObjectUpcastedUsageInfo[] objectUpcastedUsageInfos = objectUpcastedUsages(usagesIn);
    if (myPrepareSuccessfulSwingThreadCallback != null) {
      MultiMap<PsiElement, String> conflicts = new MultiMap<>();
      if (objectUpcastedUsageInfos.length > 0) {
        final String message = JavaRefactoringBundle.message("instances.of.0.upcasted.to.1.were.found",
                                                         RefactoringUIUtil.getDescription(myClass, true), CommonRefactoringUtil.htmlEmphasize(
          CommonClassNames.JAVA_LANG_OBJECT));

        conflicts.putValue(myClass, message);
      }

      analyzeConflicts(usagesIn, conflicts);
      if (!conflicts.isEmpty()) {
        ConflictsDialogBase conflictsDialog = prepareConflictsDialog(conflicts, usagesIn);
        if (!conflictsDialog.showAndGet()) {
          if (conflictsDialog.isShowConflicts()) prepareSuccessful();
          return false;
        }
      }

      if (objectUpcastedUsageInfos.length > 0) {
        showObjectUpcastedUsageView(objectUpcastedUsageInfos);
        setPreviewUsages(true);
      }
    }
    ArrayList<UsageInfo> filteredUsages = filterUsages(oldUsages);
    refUsages.set(filteredUsages.toArray(UsageInfo.EMPTY_ARRAY));
    prepareSuccessful();
    return true;
  }

  private void analyzeConflicts(UsageInfo[] usage, MultiMap<PsiElement, String> conflicts) {
    HashMap<PsiElement,HashSet<PsiElement>> reportedNonDelegatedUsages = new HashMap<>();
    HashMap<PsiClass,HashSet<PsiElement>> reportedUpcasts = new HashMap<>();
    final String classDescription = RefactoringUIUtil.getDescription(myClass, false);

    for (UsageInfo aUsage : usage) {
      final PsiElement element = aUsage.getElement();
      if (aUsage instanceof InheritanceToDelegationUsageInfo usageInfo) {
        /*if (usageInfo instanceof ObjectUpcastedUsageInfo) {
         PsiElement container = ConflictsUtil.getContainer(usageInfo.element);
         if (!reportedObjectUpcasts.contains(container)) {
           String message = "An instance of " + classDescription + " is upcasted to "
                   + nameJavaLangObject + " in " + ConflictsUtil.getDescription(container, true) + ".";
           conflicts.add(message);
           reportedObjectUpcasts.add(container);
         }
       } else*/
        if (!myIsDelegateOtherMembers && !usageInfo.getDelegateFieldAccessible().isAccessible()) {
          if (usageInfo instanceof NonDelegatedMemberUsageInfo) {
            final PsiElement nonDelegatedMember = ((NonDelegatedMemberUsageInfo)usageInfo).nonDelegatedMember;
            HashSet<PsiElement> reportedContainers = reportedNonDelegatedUsages.get(nonDelegatedMember);
            if (reportedContainers == null) {
              reportedContainers = new HashSet<>();
              reportedNonDelegatedUsages.put(nonDelegatedMember, reportedContainers);
            }
            final PsiElement container = ConflictsUtil.getContainer(element);
            if (!reportedContainers.contains(container)) {
              String message = JavaRefactoringBundle.message("0.uses.1.of.an.instance.of.a.2", RefactoringUIUtil.getDescription(container, true),
                                                         RefactoringUIUtil.getDescription(nonDelegatedMember, true), classDescription);
              conflicts.putValue(container, StringUtil.capitalize(message));
              reportedContainers.add(container);
            }
          }
          else if (usageInfo instanceof UpcastedUsageInfo) {
            final PsiClass upcastedTo = ((UpcastedUsageInfo)usageInfo).upcastedTo;
            HashSet<PsiElement> reportedContainers = reportedUpcasts.get(upcastedTo);
            if (reportedContainers == null) {
              reportedContainers = new HashSet<>();
              reportedUpcasts.put(upcastedTo, reportedContainers);
            }
            final PsiElement container = ConflictsUtil.getContainer(element);
            if (!reportedContainers.contains(container)) {
              String message = JavaRefactoringBundle.message("0.upcasts.an.instance.of.1.to.2",
                                                         RefactoringUIUtil.getDescription(container, true), classDescription,
                                                         RefactoringUIUtil.getDescription(upcastedTo, false));
              conflicts.putValue(container, StringUtil.capitalize(message));
              reportedContainers.add(container);
            }
          }
        }
      }
      else if (aUsage instanceof NoLongerOverridingSubClassMethodUsageInfo info) {
        String message = JavaRefactoringBundle.message("0.will.no.longer.override.1",
                                                   RefactoringUIUtil.getDescription(info.getSubClassMethod(), true),
                                                   RefactoringUIUtil.getDescription(info.getOverridenMethod(), true));
        conflicts.putValue(info.getSubClassMethod(), message);
      }
    }
  }

  private static ObjectUpcastedUsageInfo[] objectUpcastedUsages(UsageInfo[] usages) {
    ArrayList<ObjectUpcastedUsageInfo> result = new ArrayList<>();
    for (UsageInfo usage : usages) {
      if (usage instanceof ObjectUpcastedUsageInfo) {
        result.add(((ObjectUpcastedUsageInfo)usage));
      }
    }
    return result.toArray(new ObjectUpcastedUsageInfo[0]);
  }

  private ArrayList<UsageInfo> filterUsages(ArrayList<? extends UsageInfo> usages) {
    ArrayList<UsageInfo> result = new ArrayList<>();

    for (UsageInfo usageInfo : usages) {
      if (!(usageInfo instanceof InheritanceToDelegationUsageInfo)) {
        continue;
      }
      if (usageInfo instanceof ObjectUpcastedUsageInfo) {
        continue;
      }

      if (!myIsDelegateOtherMembers) {
        final FieldAccessibility delegateFieldAccessible = ((InheritanceToDelegationUsageInfo)usageInfo).getDelegateFieldAccessible();
        if (!delegateFieldAccessible.isAccessible()) continue;
      }

      result.add(usageInfo);
    }
    return result;
  }

  private void processClass(PsiClass inheritor, ArrayList<? super UsageInfo> usages) {
    ClassReferenceScanner scanner = new ClassReferenceSearchingScanner(inheritor);
    final MyClassInstanceReferenceVisitor instanceVisitor = new MyClassInstanceReferenceVisitor(inheritor, usages);
    scanner.processReferences(
            new ClassInstanceScanner(inheritor,
                                     instanceVisitor)
    );
    MyClassInheritorMemberReferencesVisitor classMemberVisitor = new MyClassInheritorMemberReferencesVisitor(inheritor, usages, instanceVisitor);
    inheritor.accept(classMemberVisitor);
    PsiSubstitutor inheritorSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(myClass, inheritor, PsiSubstitutor.EMPTY);

    PsiMethod[] methods = inheritor.getMethods();
    for (PsiMethod method : methods) {
      final PsiMethod baseMethod = findSuperMethodInBaseClass(method);

      if (baseMethod != null) {
        if (!baseMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
          usages.add(new NoLongerOverridingSubClassMethodUsageInfo(method, baseMethod));
        }
        else {
          final PsiMethod[] methodsByName = myClass.findMethodsByName(method.getName(), false);
          for (final PsiMethod classMethod : methodsByName) {
            final MethodSignature signature = classMethod.getSignature(inheritorSubstitutor);
            if (signature.equals(method.getSignature(PsiSubstitutor.EMPTY))) {
              if (!classMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
                usages.add(new NoLongerOverridingSubClassMethodUsageInfo(method, baseMethod));
                break;
              }
            }
          }
        }
      }
    }
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    try {
      for (UsageInfo aUsage : usages) {
        InheritanceToDelegationUsageInfo usage = (InheritanceToDelegationUsageInfo)aUsage;


        if (usage instanceof UnqualifiedNonDelegatedMemberUsageInfo) {
          delegateUsageFromClass(usage.getElement(), ((NonDelegatedMemberUsageInfo)usage).nonDelegatedMember,
                                 usage.getDelegateFieldAccessible());
        }
        else {
          upcastToDelegation(usage.getElement(), usage.getDelegateFieldAccessible());
        }
      }

      myAbstractDelegatedMethods = new HashSet<>();
      addInnerClass();
      addField(usages);
      delegateMethods();
      addImplementingInterfaces();
      updateSealedHierarchy();
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void addInnerClass() throws IncorrectOperationException {
    if (!myIsInnerClassNeeded) return;

    PsiClass innerClass = myFactory.createClass(myInnerClassName);
    final PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(myBaseClass, myClass, PsiSubstitutor.EMPTY);
    final PsiClassType superClassType = myFactory.createType(myBaseClass, superClassSubstitutor);
    final PsiJavaCodeReferenceElement baseClassReferenceElement = myFactory.createReferenceElementByType(superClassType);
    if (!myBaseClass.isInterface()) {
      innerClass.getExtendsList().add(baseClassReferenceElement);
    } else {
      innerClass.getImplementsList().add(baseClassReferenceElement);
    }
    PsiUtil.setModifierProperty(innerClass, PsiModifier.PRIVATE, true);
    innerClass = (PsiClass) myClass.add(innerClass);

    List<InnerClassMethod> innerClassMethods = getInnerClassMethods();
    for (InnerClassMethod innerClassMethod : innerClassMethods) {
      innerClassMethod.createMethod(innerClass);
    }
  }

  private void delegateUsageFromClass(PsiElement element, PsiElement nonDelegatedMember,
                                      FieldAccessibility fieldAccessibility) throws IncorrectOperationException {
    if (element instanceof PsiReferenceExpression referenceExpression) {
      if (referenceExpression.getQualifierExpression() != null) {
        upcastToDelegation(referenceExpression.getQualifierExpression(), fieldAccessibility);
      } else {
        final String name = ((PsiNamedElement) nonDelegatedMember).getName();
        final String qualifier;
        if (isStatic (nonDelegatedMember)) {
          qualifier = myBaseClass.getName();
        }
        else if (!fieldAccessibility.isAccessible() && myGenerateGetter) {
          qualifier = myGetterName + "()";
        }
        else {
          qualifier = myFieldName;
        }

        PsiExpression newExpr = myFactory.createExpressionFromText(qualifier + "." + name, element);
        newExpr = (PsiExpression) CodeStyleManager.getInstance(myProject).reformat(newExpr);
        element.replace(newExpr);
      }
    }
    else if (element instanceof PsiJavaCodeReferenceElement) {
        final String name = ((PsiNamedElement) nonDelegatedMember).getName();

      PsiElement parent = element.getParent ();
      if (!isStatic (nonDelegatedMember) && parent instanceof PsiNewExpression newExpr) {
        if (newExpr.getQualifier() != null) {
          upcastToDelegation(newExpr.getQualifier(), fieldAccessibility);
        } else {
          final String qualifier;
          if (!fieldAccessibility.isAccessible() && myGenerateGetter) {
            qualifier = myGetterName + "()";
          }
          else {
            qualifier = myFieldName;
          }
          newExpr.replace(myFactory.createExpressionFromText(qualifier + "." + newExpr.getText(), parent));
        }
      }
      else {
        final String qualifier = myBaseClass.getName();
        PsiJavaCodeReferenceElement newRef = myFactory.createFQClassNameReferenceElement(qualifier + "." + name, element.getResolveScope ());
        //newRef = (PsiJavaCodeReferenceElement) CodeStyleManager.getInstance(myProject).reformat(newRef);
        element.replace(newRef);
      }
    } else {
      LOG.assertTrue(false);
    }
  }

  private static boolean isStatic(PsiElement member) {
    return member instanceof PsiModifierListOwner method && method.hasModifierProperty(PsiModifier.STATIC);
  }

  private void upcastToDelegation(PsiElement element, FieldAccessibility fieldAccessibility) throws IncorrectOperationException {
    final PsiExpression expression = (PsiExpression) element;

    final PsiExpression newExpr;
    final PsiReferenceExpression ref;
    final String delegateQualifier;
    if (!(expression instanceof PsiQualifiedExpression)) {
      delegateQualifier = "a.";
    } else {
      PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(myProject).getResolveHelper();
      final PsiVariable psiVariable = resolveHelper.resolveReferencedVariable(myFieldName, element);
      if (psiVariable == null) {
        delegateQualifier = "";
      } else {
        delegateQualifier = "a.";
      }
    }
    if (!fieldAccessibility.isAccessible() && myGenerateGetter) {
      newExpr = myFactory.createExpressionFromText(delegateQualifier + myGetterName + "()", expression);
      ref = (PsiReferenceExpression) ((PsiMethodCallExpression) newExpr).getMethodExpression().getQualifierExpression();
    } else {
      newExpr = myFactory.createExpressionFromText(delegateQualifier + myFieldName, expression);
      ref = (PsiReferenceExpression) ((PsiReferenceExpression) newExpr).getQualifierExpression();
    }
//    LOG.debug("upcastToDelegation:" + element + ":newExpr = " + newExpr);
//    LOG.debug("upcastToDelegation:" + element + ":ref = " + ref);
    if (ref != null) {
      ref.replace(expression);
    }
    expression.replace(newExpr);
//    LOG.debug("upcastToDelegation:" + element + ":replaced = " + replaced);
  }

  private void delegateMethods() throws IncorrectOperationException {
    for (PsiMethod method : myDelegatedMethods) {
      if (!myAbstractDelegatedMethods.contains(method)) {
        PsiMethod methodToAdd = delegateMethod(myFieldName, method, getSuperSubstitutor(method.getContainingClass()));

        @PsiModifier.ModifierConstant String visibility = myDelegatedMethodsVisibility.get(method);
        if (visibility != null) {
          PsiUtil.setModifierProperty(methodToAdd, visibility, true);
        }

        myClass.add(methodToAdd);
      }
    }
  }

  private PsiMethod delegateMethod(String delegationTarget,
                                   PsiMethod method,
                                   PsiSubstitutor substitutor) throws IncorrectOperationException {
    substitutor = OverrideImplementExploreUtil.correctSubstitutor(method, substitutor);
    PsiMethod methodToAdd = GenerateMembersUtil.substituteGenericMethod(method, substitutor);

    methodToAdd.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, false);

    NullableNotNullManager.getInstance(myProject).copyNullableOrNotNullAnnotation(method, methodToAdd);

    final String delegationBody = getDelegationBody(methodToAdd, delegationTarget);
    PsiCodeBlock newBody = myFactory.createCodeBlockFromText(delegationBody, method);

    PsiCodeBlock oldBody = methodToAdd.getBody();
    if (oldBody != null) {
      oldBody.replace(newBody);
    }
    else {
      methodToAdd.addBefore(newBody, null);
    }

    if (methodToAdd.getDocComment() != null) methodToAdd.getDocComment().delete();
    methodToAdd = (PsiMethod)CodeStyleManager.getInstance(myProject).reformat(methodToAdd);
    methodToAdd = (PsiMethod)JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(methodToAdd);
    return methodToAdd;
  }

  private static String getDelegationBody(PsiMethod methodToAdd, String delegationTarget) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("{\n");

    if (!PsiTypes.voidType().equals(methodToAdd.getReturnType())) {
      buffer.append("return ");
    }

    buffer.append(delegationTarget);
    buffer.append(".");
    buffer.append(methodToAdd.getName());
    buffer.append("(");
    PsiParameter[] params = methodToAdd.getParameterList().getParameters();
    for (int i = 0; i < params.length; i++) {
      PsiParameter param = params[i];
      if (i > 0) {
        buffer.append(",");
      }
      buffer.append(param.getName());
    }
    buffer.append(");\n}");
    return buffer.toString();
  }

  private void addImplementingInterfaces() throws IncorrectOperationException {
    final PsiReferenceList implementsList = myClass.getImplementsList();
    LOG.assertTrue(implementsList != null);
    for (PsiClass delegatedInterface : myDelegatedInterfaces) {
      if (!myClassImplementedInterfaces.contains(delegatedInterface)) {
        implementsList.add(myFactory.createClassReferenceElement(delegatedInterface));
      }
    }

    if (!myBaseClass.isInterface()) {
      final PsiReferenceList extendsList = myClass.getExtendsList();
      LOG.assertTrue(extendsList != null);
      extendsList.getReferenceElements()[0].delete();
    } else {
      final PsiJavaCodeReferenceElement[] interfaceRefs = implementsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement interfaceRef : interfaceRefs) {
        final PsiElement resolved = interfaceRef.resolve();
        if (myManager.areElementsEquivalent(myBaseClass, resolved)) {
          interfaceRef.delete();
          break;
        }
      }
    }
  }

  private void updateSealedHierarchy() {
    if (!myBaseClass.hasModifierProperty(PsiModifier.SEALED)) return;
    SealedUtils.removeFromPermitsList(myBaseClass, myClass);
    PsiModifierList modifiers = myClass.getModifierList();
    if (modifiers == null) return;
    if (!modifiers.hasExplicitModifier(PsiModifier.NON_SEALED) || SealedUtils.hasSealedParent(myClass)) return;
    modifiers.setModifierProperty(PsiModifier.NON_SEALED, false);
  }

  private void addField(UsageInfo[] usages) throws IncorrectOperationException {
    final String fieldVisibility = getFieldVisibility(usages);

    final boolean fieldInitializerNeeded = isFieldInitializerNeeded();

    PsiField field = createField(fieldVisibility, fieldInitializerNeeded, defaultClassFieldType());

    if (!myIsInnerClassNeeded) {
      field.getTypeElement().replace(myFactory.createTypeElement(myBaseClassType));
      if (fieldInitializerNeeded) {
        final PsiJavaCodeReferenceElement classReferenceElement = myFactory.createReferenceElementByType(myBaseClassType);
        PsiNewExpression newExpression = (PsiNewExpression) field.getInitializer();
        newExpression.getClassReference().replace(classReferenceElement);
      }
    }

    field = (PsiField) CodeStyleManager.getInstance(myProject).reformat(field);
    myClass.add(field);
    if (!fieldInitializerNeeded) {
      fixConstructors();
    }

    if (myGenerateGetter) {
      final String getterVisibility = PsiModifier.PUBLIC;
      StringBuilder getterBuffer = new StringBuilder();
      getterBuffer.append(getterVisibility);
      getterBuffer.append(" Object ");
      getterBuffer.append(myGetterName);
      getterBuffer.append("() {\n return ");
      getterBuffer.append(myFieldName);
      getterBuffer.append(";\n}");
      PsiMethod getter = myFactory.createMethodFromText(getterBuffer.toString(), myClass);
      getter.getReturnTypeElement().replace(myFactory.createTypeElement(myBaseClassType));
      getter = (PsiMethod) CodeStyleManager.getInstance(myProject).reformat(getter);
      myClass.add(getter);
    }
  }

  private String getFieldVisibility(UsageInfo[] usages) {
    if (myIsDelegateOtherMembers && !myGenerateGetter) {
      return PsiModifier.PUBLIC;
    }

    for (UsageInfo aUsage : usages) {
      InheritanceToDelegationUsageInfo usage = (InheritanceToDelegationUsageInfo)aUsage;
      final FieldAccessibility delegateFieldAccessible = usage.getDelegateFieldAccessible();
      if (delegateFieldAccessible.isAccessible() && delegateFieldAccessible.getContainingClass() != myClass) {
        return PsiModifier.PROTECTED;
      }
    }
    return PsiModifier.PRIVATE;
  }

  private String defaultClassFieldType() {
    return (myIsInnerClassNeeded ? myInnerClassName : "Object");
  }

  private PsiField createField(final String fieldVisibility, final boolean fieldInitializerNeeded, String defaultTypeName) throws IncorrectOperationException {
    StringBuilder buffer = new StringBuilder();
    buffer.append(fieldVisibility);
    buffer.append(" final ").append(defaultTypeName).append("  ");
    buffer.append(myFieldName);
    if (fieldInitializerNeeded) {
      buffer.append(" = new ").append(defaultTypeName).append("()");
    }
    buffer.append(";");
    return myFactory.createFieldFromText(buffer.toString(), myClass);
  }

  private void fixConstructors() throws IncorrectOperationException {
    if (myBaseClass.isInterface()) return;
    final PsiJavaCodeReferenceElement baseClassReference = myFactory.createClassReferenceElement(myBaseClass);

    PsiMethod[] constructors = myClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      PsiCodeBlock body = constructor.getBody();
      final PsiStatement[] statements = body.getStatements();
      String fieldQualifier = "";
      PsiParameter[] constructorParams = constructor.getParameterList().getParameters();
      for (PsiParameter constructorParam : constructorParams) {
        if (myFieldName.equals(constructorParam.getName())) {
          fieldQualifier = "this.";
          break;
        }
      }
      final String assignmentText = fieldQualifier + myFieldName + "= new " + defaultClassFieldType() + "()";
      if (statements.length < 1 || !JavaHighlightUtil.isSuperOrThisCall(statements[0], true, true) || myBaseClass.isInterface()) {
        PsiExpressionStatement assignmentStatement =
          (PsiExpressionStatement)myFactory.createStatementFromText(
            assignmentText, body
          );
        if (!myIsInnerClassNeeded) {
          final PsiAssignmentExpression assignmentExpr = (PsiAssignmentExpression)assignmentStatement.getExpression();
          final PsiNewExpression newExpression = (PsiNewExpression)assignmentExpr.getRExpression();
          assert newExpression != null;
          final PsiJavaCodeReferenceElement classRef = newExpression.getClassReference();
          assert classRef != null;
          classRef.replace(baseClassReference);
        }

        assignmentStatement = (PsiExpressionStatement)CodeStyleManager.getInstance(myProject).reformat(assignmentStatement);
        if (statements.length > 0) {
          if (!JavaHighlightUtil.isSuperOrThisCall(statements[0], true, false)) {
            body.addBefore(assignmentStatement, statements[0]);
          }
          else {
            body.addAfter(assignmentStatement, statements[0]);
          }
        }
        else {
          body.add(assignmentStatement);
        }
      }
      else {
        final PsiExpressionStatement callStatement = ((PsiExpressionStatement)statements[0]);
        if (!JavaHighlightUtil.isSuperOrThisCall(callStatement, false, true)) {
          final PsiMethodCallExpression superConstructorCall =
            (PsiMethodCallExpression)callStatement.getExpression();
          PsiAssignmentExpression assignmentExpression =
            (PsiAssignmentExpression)myFactory.createExpressionFromText(
              assignmentText, superConstructorCall
            );
          PsiNewExpression newExpression =
            (PsiNewExpression)assignmentExpression.getRExpression();
          if (!myIsInnerClassNeeded) {
            newExpression.getClassReference().replace(baseClassReference);
          }
          assignmentExpression = (PsiAssignmentExpression)CodeStyleManager.getInstance(myProject).reformat(assignmentExpression);
          newExpression.getArgumentList().replace(superConstructorCall.getArgumentList());
          superConstructorCall.replace(assignmentExpression);
        }
      }
    }
  }

  private boolean isFieldInitializerNeeded() {
    if (myBaseClass.isInterface()) return true;
    PsiMethod[] constructors = myClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      final PsiStatement[] statements = constructor.getBody().getStatements();
      if (statements.length > 0 && JavaHighlightUtil.isSuperOrThisCall(statements[0], true, false)) return false;
    }
    return true;
  }

  private List<InnerClassMethod> getInnerClassMethods() {
    ArrayList<InnerClassMethod> result = new ArrayList<>();

    // find all neccessary constructors
    if (!myBaseClass.isInterface()) {
      PsiMethod[] constructors = myClass.getConstructors();
      for (PsiMethod constructor : constructors) {
        final PsiStatement[] statements = constructor.getBody().getStatements();
        if (statements.length > 0 && JavaHighlightUtil.isSuperOrThisCall(statements[0], true, false)) {
          final PsiMethodCallExpression superConstructorCall =
            (PsiMethodCallExpression)((PsiExpressionStatement)statements[0]).getExpression();
          PsiElement superConstructor = superConstructorCall.getMethodExpression().resolve();
          if (superConstructor instanceof PsiMethod && ((PsiMethod)superConstructor).isConstructor()) {
            result.add(new InnerClassConstructor((PsiMethod)superConstructor));
          }
        }
      }
    }

    // find overriding/implementing method
    {
      class InnerClassOverridingMethod extends InnerClassMethod {
        InnerClassOverridingMethod(PsiMethod method) {
          super(method);
        }

        @Override
        public void createMethod(PsiClass innerClass)
                throws IncorrectOperationException {
          OverriddenMethodClassMemberReferencesVisitor visitor = new OverriddenMethodClassMemberReferencesVisitor();
          myClass.accept(visitor);
          final List<PsiAction> actions = visitor.getPsiActions();
          for (PsiAction action : actions) {
            action.run();
          }
          innerClass.add(myMethod);
          myMethod.delete();
          // myMethod.replace(delegateMethod(myMethod));
        }
      }

      for (PsiMethod method : myOverriddenMethods) {
        result.add(new InnerClassOverridingMethod(method));
      }
    }

    // fix abstract methods
    {
      class InnerClassAbstractMethod extends InnerClassMethod {
        private final boolean myImplicitImplementation;

        InnerClassAbstractMethod(PsiMethod method, final boolean implicitImplementation) {
          super(method);
          myImplicitImplementation = implicitImplementation;
        }

        @Override
        public void createMethod(PsiClass innerClass)
                throws IncorrectOperationException {
          PsiSubstitutor substitutor = getSuperSubstitutor(myMethod.getContainingClass());
          PsiMethod method = delegateMethod(myClass.getName() + ".this", myMethod, substitutor);
          final PsiClass containingClass = myMethod.getContainingClass();
          if (myBaseClass.isInterface() || containingClass.isInterface()) {
            PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true);
          }
          innerClass.add(method);
          if (!myImplicitImplementation) {
            final MethodSignature signature = myMethod.getSignature(substitutor);
            PsiMethod outerMethod = MethodSignatureUtil.findMethodBySignature(myClass, signature, false);
            if (outerMethod == null) {
              String visibility = checkOuterClassAbstractMethod(signature);
              PsiMethod newOuterMethod = (PsiMethod)myClass.add(myMethod);
              PsiUtil.setModifierProperty(newOuterMethod, visibility, true);
              if (containingClass.isInterface() &&
                  !innerClass.isInterface() &&
                  myMethod.getBody() == null) {
                PsiUtil.setModifierProperty(newOuterMethod, PsiModifier.ABSTRACT, true);
              }
              final PsiDocComment docComment = newOuterMethod.getDocComment();
              if (docComment != null) {
                docComment.delete();
              }
            }
          }
        }

      }
      PsiMethod[] methods = myBaseClass.getAllMethods();

      for (PsiMethod method : methods) {
        if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
          final MethodSignature signature = method.getSignature(getSuperSubstitutor(method.getContainingClass()));
          PsiMethod classMethod = MethodSignatureUtil.findMethodBySignature(myClass, signature, true);
          if (classMethod == null || classMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
            result.add(new InnerClassAbstractMethod(method, false));
          }
          else if ((myBaseClass.isInterface() && classMethod.getContainingClass() != myClass)) {   // IDEADEV-19675
            result.add(new InnerClassAbstractMethod(method, true));
          }
        }
      }
    }


    return result;
  }

  private void showObjectUpcastedUsageView(final ObjectUpcastedUsageInfo[] usages) {
    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTargetsNodeText(JavaRefactoringBundle.message("replacing.inheritance.with.delegation"));
    presentation.setCodeUsagesString(JavaRefactoringBundle.message("instances.casted.to.java.lang.object"));
    final String upcastedString = JavaRefactoringBundle.message("instances.upcasted.to.object");
    presentation.setUsagesString(upcastedString);
    presentation.setTabText(upcastedString);

    UsageViewManager manager = UsageViewManager.getInstance(myProject);
    manager.showUsages(
      new UsageTarget[]{new PsiElement2UsageTargetAdapter(myClass)},
      UsageInfoToUsageConverter.convert(new PsiElement[]{myClass}, usages),
      presentation
    );

    WindowManager.getInstance().getStatusBar(myProject).setInfo(JavaRefactoringBundle.message("instances.upcasted.to.java.lang.object.found"));
  }

  /**
   *
   * @return Visibility
   */
  @PsiModifier.ModifierConstant
  private String checkOuterClassAbstractMethod(MethodSignature methodSignature) {
    String visibility = PsiModifier.PROTECTED;
    for (PsiMethod method : myDelegatedMethods) {
      MethodSignature otherSignature = method.getSignature(getSuperSubstitutor(method.getContainingClass()));

      if (MethodSignatureUtil.areSignaturesEqual(otherSignature, methodSignature)) {
        visibility = VisibilityUtil.getHighestVisibility(visibility,
                                                         VisibilityUtil.getVisibilityModifier(method.getModifierList()));
        myAbstractDelegatedMethods.add(method);
      }
    }
    return visibility;
  }

  private Set<PsiMethod> getOverriddenMethods() {
    LinkedHashSet<PsiMethod> result = new LinkedHashSet<>();

    PsiMethod[] methods = myClass.getMethods();
    for (PsiMethod method : methods) {
      if (findSuperMethodInBaseClass(method) != null) result.add(method);
    }
    return result;
  }

  @Nullable
  private PsiMethod findSuperMethodInBaseClass (PsiMethod method) {
    final PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      PsiClass containingClass = superMethod.getContainingClass();
      if (InheritanceUtil.isInheritorOrSelf(myBaseClass, containingClass, true)) {
        String qName = containingClass.getQualifiedName();
        if (!CommonClassNames.JAVA_LANG_OBJECT.equals(qName)) {
          return superMethod;
        }
      }
    }
    return null;
  }


  @Override
  @NotNull
  protected String getCommandName() {
    return JavaRefactoringBundle.message("replace.inheritance.with.delegation.command", DescriptiveNameUtil.getDescriptiveName(myClass));
  }

  private Set<PsiMember> getAllBaseClassMembers() {
    HashSet<PsiMember> result = new HashSet<>();
    Collections.addAll(result, (PsiMember[])myBaseClass.getAllFields());
    Collections.addAll(result, (PsiMember[])myBaseClass.getAllInnerClasses());
    Collections.addAll(result, (PsiMember[])myBaseClass.getAllMethods());

    //remove java.lang.Object members
    for (Iterator<PsiMember> iterator = result.iterator(); iterator.hasNext();) {
      PsiMember member = iterator.next();
      if (CommonClassNames.JAVA_LANG_OBJECT.equals(member.getContainingClass().getQualifiedName())) {
        iterator.remove();
      }
    }
    return Collections.unmodifiableSet(result);
  }

  private Set<PsiClass> getAllBases() {
    HashSet<PsiClass> temp = new HashSet<>();
    InheritanceUtil.getSuperClasses(myBaseClass, temp, true);
    temp.add(myBaseClass);
    return Collections.unmodifiableSet(temp);
  }

  private boolean isDelegated(PsiMember classMember) {
    if(!(classMember instanceof PsiMethod method)) return false;
    for (PsiMethod delegatedMethod : myDelegatedMethods) {
      //methods reside in base class, so no substitutor needed
      if (MethodSignatureUtil.areSignaturesEqual(method.getSignature(PsiSubstitutor.EMPTY),
                                                 delegatedMethod.getSignature(PsiSubstitutor.EMPTY))) {
        return true;
      }
    }
    return false;
  }

  private class MyClassInheritorMemberReferencesVisitor extends ClassMemberReferencesVisitor {
    private final List<? super UsageInfo> myUsageInfoStorage;
    private final ClassInstanceScanner.ClassInstanceReferenceVisitor myInstanceVisitor;

    MyClassInheritorMemberReferencesVisitor(PsiClass aClass, List<? super UsageInfo> usageInfoStorage,
                                            ClassInstanceScanner.ClassInstanceReferenceVisitor instanceScanner) {
      super(aClass);

      myUsageInfoStorage = usageInfoStorage;
      myInstanceVisitor = instanceScanner;
    }

    @Override
    protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
      if ("super".equals(classMemberReference.getText()) && classMemberReference.getParent() instanceof PsiMethodCallExpression) {
        return;
      }

      if (classMember != null && myBaseClassMembers.contains(classMember) && !isDelegated(classMember)) {
        final FieldAccessibility delegateFieldVisibility = new FieldAccessibility(true, getPsiClass());
        final InheritanceToDelegationUsageInfo usageInfo;
        if (classMemberReference instanceof PsiReferenceExpression &&
            ((PsiReferenceExpression)classMemberReference).getQualifierExpression() != null) {
          usageInfo = new NonDelegatedMemberUsageInfo(((PsiReferenceExpression)classMemberReference).getQualifierExpression(),
                                                      classMember, delegateFieldVisibility);
        }
        else {
          usageInfo = new UnqualifiedNonDelegatedMemberUsageInfo(classMemberReference, classMember,
                                                                 delegateFieldVisibility);
        }
        myUsageInfoStorage.add(usageInfo);
      }
    }

    @Override public void visitThisExpression(@NotNull PsiThisExpression expression) {
      ClassInstanceScanner.processNonArrayExpression(myInstanceVisitor, expression, null);
    }
  }

  private class MyClassMemberReferencesVisitor extends MyClassInheritorMemberReferencesVisitor {
    MyClassMemberReferencesVisitor(List<? super UsageInfo> usageInfoStorage,
                                   ClassInstanceScanner.ClassInstanceReferenceVisitor instanceScanner) {
      super(InheritanceToDelegationProcessor.this.myClass, usageInfoStorage, instanceScanner);
    }

    @Override public void visitMethod(@NotNull PsiMethod method) {
      if (!myOverriddenMethods.contains(method)) {
        super.visitMethod(method);
      }
    }
  }

  interface PsiAction {
    void run() throws IncorrectOperationException;
  }

  /**
   * This visitor should be called for overridden methods before they are moved to an inner class
   */
  private class OverriddenMethodClassMemberReferencesVisitor extends ClassMemberReferencesVisitor {
    private final ArrayList<PsiAction> myPsiActions;
    private final PsiThisExpression myQualifiedThis;

    OverriddenMethodClassMemberReferencesVisitor() throws IncorrectOperationException {
      super(myClass);
      myPsiActions = new ArrayList<>();
      final PsiJavaCodeReferenceElement classReferenceElement = myFactory.createClassReferenceElement(myClass);
      myQualifiedThis = (PsiThisExpression) myFactory.createExpressionFromText("A.this", null);
      myQualifiedThis.getQualifier().replace(classReferenceElement);
    }

    public List<PsiAction> getPsiActions() {
      return myPsiActions;
    }

    class QualifyThis implements PsiAction {
      private final PsiThisExpression myThisExpression;

      QualifyThis(PsiThisExpression thisExpression) {
        myThisExpression = thisExpression;
      }

      @Override
      public void run() throws IncorrectOperationException {
        myThisExpression.replace(myQualifiedThis);
      }
    }

    class QualifyName implements PsiAction {
      private final PsiReferenceExpression myRef;
      private final String myReferencedName;

      QualifyName(PsiReferenceExpression ref, String name) {
        myRef = ref;
        myReferencedName = name;
      }

      @Override
      public void run() throws IncorrectOperationException {
        PsiReferenceExpression newRef =
                (PsiReferenceExpression) myFactory.createExpressionFromText("a." + myReferencedName, null);
        newRef.getQualifierExpression().replace(myQualifiedThis);
        myRef.replace(newRef);
      }
    }

    class QualifyWithField implements PsiAction {
      private final PsiReferenceExpression myReference;
      private final String myReferencedName;

      QualifyWithField(final PsiReferenceExpression reference, final String name) {
        myReference = reference;
        myReferencedName = name;
      }

      @Override
      public void run() throws IncorrectOperationException {
        PsiReferenceExpression newRef =
                (PsiReferenceExpression) myFactory.createExpressionFromText(myFieldName + "." + myReferencedName, null);
        myReference.replace(newRef);
      }
    }

    @Override
    protected void visitClassMemberReferenceExpression(PsiMember classMember,
                                                       PsiReferenceExpression classMemberReference) {
      if (classMember instanceof PsiField field) {
        if (field.getContainingClass().equals(myClass)) {
          final String name = field.getName();
          final PsiField baseField = myBaseClass.findFieldByName(name, true);
          if (baseField != null) {
            myPsiActions.add(new QualifyName(classMemberReference, name));
          } else if (classMemberReference.getQualifierExpression() instanceof PsiThisExpression) {
            myPsiActions.add(new QualifyThis((PsiThisExpression) classMemberReference.getQualifierExpression()));
          }
        }
      } else if (classMember instanceof PsiMethod method) {
        if (method.getContainingClass().equals(myClass)) {
          if (!myOverriddenMethods.contains(method)) {
            final PsiMethod baseMethod = findSuperMethodInBaseClass(method);
            if (baseMethod != null) {
              myPsiActions.add(new QualifyName(classMemberReference, baseMethod.getName()));
            } else if (classMemberReference.getQualifierExpression() instanceof PsiThisExpression) {
              myPsiActions.add(new QualifyThis((PsiThisExpression) classMemberReference.getQualifierExpression()));
            }
          }
          else if (!myDelegatedMethods.contains(method)) {
            myPsiActions.add(new QualifyWithField(classMemberReference, method.getName()));
          }
        }
      }
    }

    @Override public void visitThisExpression(final @NotNull PsiThisExpression expression) {
      class Visitor implements ClassInstanceScanner.ClassInstanceReferenceVisitor {
        @Override
        public void visitQualifier(PsiReferenceExpression qualified, PsiExpression instanceRef, PsiElement referencedInstance) {
          LOG.assertTrue(false);
        }

        @Override
        public void visitTypeCast(PsiTypeCastExpression typeCastExpression, PsiExpression instanceRef, PsiElement referencedInstance) {
          processType(typeCastExpression.getCastType().getType());
        }

        @Override
        public void visitReadUsage(PsiExpression instanceRef, PsiType expectedType, PsiElement referencedInstance) {
          processType(expectedType);
        }

        @Override
        public void visitWriteUsage(PsiExpression instanceRef, PsiType assignedType, PsiElement referencedInstance) {
          LOG.assertTrue(false);
        }

        private void processType(PsiType type) {
          final PsiClass resolved = PsiUtil.resolveClassInType(type);
          if (resolved != null && !myBaseClassBases.contains(resolved)) {
            myPsiActions.add(new QualifyThis(expression));
          }
        }
      }
      Visitor visitor = new Visitor();
      ClassInstanceScanner.processNonArrayExpression(visitor, expression, null);
    }

    @Override
    protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
    }

  }


  private final class MyClassInstanceReferenceVisitor implements ClassInstanceScanner.ClassInstanceReferenceVisitor {
    private final PsiClass myClass;
    private final List<? super UsageInfo> myUsageInfoStorage;
    private final Set<PsiClass> myImplementedInterfaces;

    MyClassInstanceReferenceVisitor(PsiClass aClass, List<? super UsageInfo> usageInfoStorage) {
      myClass = aClass;
      myUsageInfoStorage = usageInfoStorage;
      myImplementedInterfaces = getImplementedInterfaces();
    }

    public Set<PsiClass> getImplementedInterfaces() {
      PsiClass aClass = myClass;
      HashSet<PsiClass> result = new HashSet<>();
      while (aClass != null && !myManager.areElementsEquivalent(aClass, myBaseClass)) {
        final PsiClassType[] implementsTypes = aClass.getImplementsListTypes();
        for (PsiClassType implementsType : implementsTypes) {
          PsiClass resolved = implementsType.resolve();
          if (resolved != null && !myManager.areElementsEquivalent(resolved, myBaseClass)) {
            result.add(resolved);
            InheritanceUtil.getSuperClasses(resolved, result, true);
          }
        }

        aClass = aClass.getSuperClass();
      }
      return result;
    }


    @Override
    public void visitQualifier(PsiReferenceExpression qualified, PsiExpression instanceRef, PsiElement referencedInstance) {
      final PsiExpression qualifierExpression = qualified.getQualifierExpression();

      // do not add usages inside a class
      if (qualifierExpression == null
          || qualifierExpression instanceof PsiThisExpression
          || qualifierExpression instanceof PsiSuperExpression) {
        return;
      }

      PsiElement resolved = qualified.resolve();
      if (resolved != null && (myBaseClassMembers.contains(resolved) || myOverriddenMethods.contains(resolved))
          && !isDelegated((PsiMember)resolved)) {
        myUsageInfoStorage.add(new NonDelegatedMemberUsageInfo(instanceRef, resolved, getFieldAccessibility(instanceRef)));
      }
    }

    @Override
    public void visitTypeCast(PsiTypeCastExpression typeCastExpression, PsiExpression instanceRef, PsiElement referencedInstance) {
      processTypedUsage(typeCastExpression.getCastType().getType(), instanceRef);
    }


    @Override
    public void visitReadUsage(PsiExpression instanceRef, PsiType expectedType, PsiElement referencedInstance) {
      processTypedUsage(expectedType, instanceRef);
    }

    @Override
    public void visitWriteUsage(PsiExpression instanceRef, PsiType assignedType, PsiElement referencedInstance) {
    }

    private void processTypedUsage(PsiType type, PsiExpression instanceRef) {
      final PsiClass aClass = PsiUtil.resolveClassInType(type);
      if (aClass == null) return;
      String qName = aClass.getQualifiedName();
      if (CommonClassNames.JAVA_LANG_OBJECT.equals(qName)) {
        myUsageInfoStorage.add(new ObjectUpcastedUsageInfo(instanceRef, aClass, getFieldAccessibility(instanceRef)));
      } else {
        if (myBaseClassBases.contains(aClass)
            && !myImplementedInterfaces.contains(aClass) && !myDelegatedInterfaces.contains(aClass)) {
          myUsageInfoStorage.add(new UpcastedUsageInfo(instanceRef, aClass, getFieldAccessibility(instanceRef)));
        }
      }
    }
  }
}