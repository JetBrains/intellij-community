// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeErrorContext;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.containers.MostlySingularMultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MethodChecker {
  private final @NotNull JavaErrorVisitor myVisitor;
  private final @NotNull Map<PsiClass, MostlySingularMultiMap<MethodSignature, PsiMethod>> myDuplicateMethods = new HashMap<>();
  private static final MethodSignature ourValuesEnumSyntheticMethod =
    MethodSignatureUtil.createMethodSignature("values",
                                              PsiType.EMPTY_ARRAY,
                                              PsiTypeParameter.EMPTY_ARRAY,
                                              PsiSubstitutor.EMPTY);

  MethodChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  private @NotNull MostlySingularMultiMap<MethodSignature, PsiMethod> getDuplicateMethods(@NotNull PsiClass aClass) {
    MostlySingularMultiMap<MethodSignature, PsiMethod> signatures = myDuplicateMethods.get(aClass);
    if (signatures == null) {
      signatures = new MostlySingularMultiMap<>();
      for (PsiMethod method : aClass.getMethods()) {
        if (method instanceof ExternallyDefinedPsiElement) continue; // ignore aspectj-weaved methods; they are checked elsewhere
        MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
        signatures.add(signature, method);
      }

      myDuplicateMethods.put(aClass, signatures);
    }
    return signatures;
  }

  void checkMustBeThrowable(@NotNull PsiClass aClass, @NotNull PsiElement context) {
    PsiClassType type = JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
    PsiClassType throwable = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_THROWABLE, context.getResolveScope());
    if (!TypeConversionUtil.isAssignable(throwable, type)) {
      if (IncompleteModelUtil.isIncompleteModel(context) && IncompleteModelUtil.isPotentiallyConvertible(throwable, type, context)) return;
      myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(context, new JavaIncompatibleTypeErrorContext(throwable, type)));
    }
  }

  void checkMethodCanHaveBody(@NotNull PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    boolean hasNoBody = method.getBody() == null;
    boolean isInterface = aClass != null && aClass.isInterface();
    boolean isExtension = method.hasModifierProperty(PsiModifier.DEFAULT);
    boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    boolean isPrivate = method.hasModifierProperty(PsiModifier.PRIVATE);
    boolean isConstructor = method.isConstructor();

    if (hasNoBody) {
      if (isExtension) {
        myVisitor.report(JavaErrorKinds.METHOD_DEFAULT_SHOULD_HAVE_BODY.create(method));
      }
      else if (isInterface) {
        if (isStatic && myVisitor.isApplicable(JavaFeature.STATIC_INTERFACE_CALLS)) {
          myVisitor.report(JavaErrorKinds.METHOD_STATIC_IN_INTERFACE_SHOULD_HAVE_BODY.create(method));
        }
        else if (isPrivate && myVisitor.isApplicable(JavaFeature.PRIVATE_INTERFACE_METHODS)) {
          myVisitor.report(JavaErrorKinds.METHOD_PRIVATE_IN_INTERFACE_SHOULD_HAVE_BODY.create(method));
        }
      }
    }
    else if (isInterface) {
      if (!isExtension && !isStatic && !isPrivate && !isConstructor) {
        myVisitor.report(JavaErrorKinds.METHOD_INTERFACE_BODY.create(method));
      }
    }
    else if (isExtension) {
      myVisitor.report(JavaErrorKinds.METHOD_DEFAULT_IN_CLASS.create(method));
    }
    else if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      myVisitor.report(JavaErrorKinds.METHOD_ABSTRACT_BODY.create(method));
    }
    else if (method.hasModifierProperty(PsiModifier.NATIVE)) {
      myVisitor.report(JavaErrorKinds.METHOD_NATIVE_BODY.create(method));
    }
  }

  void checkMethodMustHaveBody(@NotNull PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    if (method.getBody() != null
        || method.hasModifierProperty(PsiModifier.ABSTRACT)
        || method.hasModifierProperty(PsiModifier.NATIVE)
        || aClass == null
        || aClass.isInterface()
        || PsiUtilCore.hasErrorElementChild(method)) {
      return;
    }
    boolean abstractAllowed = !aClass.isRecord() && !method.isConstructor() && !(aClass instanceof PsiAnonymousClass);
    myVisitor.report(abstractAllowed ? JavaErrorKinds.METHOD_SHOULD_HAVE_BODY_OR_ABSTRACT.create(method) :
                     JavaErrorKinds.METHOD_SHOULD_HAVE_BODY.create(method));
  }

  void checkStaticMethodOverride(@NotNull PsiMethod method) {
    // constructors are not members and therefore don't override class methods
    if (method.isConstructor()) return;

    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return;
    HierarchicalMethodSignature methodSignature = PsiSuperMethodImplUtil.getHierarchicalMethodSignature(method);
    List<HierarchicalMethodSignature> superSignatures = methodSignature.getSuperSignatures();
    if (superSignatures.isEmpty()) return;

    boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    for (HierarchicalMethodSignature signature : superSignatures) {
      PsiMethod superMethod = signature.getMethod();
      PsiClass superClass = superMethod.getContainingClass();
      if (superClass == null) continue;
      checkStaticMethodOverride(aClass, method, isStatic, superClass, superMethod);
    }
  }

  private void checkStaticMethodOverride(@NotNull PsiClass aClass,
                                         @NotNull PsiMethod method,
                                         boolean isMethodStatic,
                                         @NotNull PsiClass superClass,
                                         @NotNull PsiMethod superMethod) {
    PsiModifierList superModifierList = superMethod.getModifierList();
    if (superModifierList.hasModifierProperty(PsiModifier.PRIVATE)) return;
    if (superModifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
        && !JavaPsiFacade.getInstance(myVisitor.project()).arePackagesTheSame(aClass, superClass)) return;
    boolean isSuperMethodStatic = superModifierList.hasModifierProperty(PsiModifier.STATIC);
    if (isMethodStatic != isSuperMethodStatic) {
      var errorKind = isMethodStatic ? JavaErrorKinds.METHOD_STATIC_OVERRIDES_INSTANCE : JavaErrorKinds.METHOD_INSTANCE_OVERRIDES_STATIC;
      myVisitor.report(errorKind.create(method, superMethod));
      return;
    }

    if (isMethodStatic) {
      if (superClass.isInterface()) return;
      checkIsWeaker(method, superMethod);
      if (!myVisitor.hasErrorResults()) checkSuperMethodIsFinal(method, superMethod);
    }
  }

  private void checkSuperMethodIsFinal(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    // strange things happen when super method is from Object and method from interface
    if (superMethod.hasModifierProperty(PsiModifier.FINAL)) {
      myVisitor.report(JavaErrorKinds.METHOD_OVERRIDES_FINAL.create(method, superMethod));
    }
  }

  private void checkIsWeaker(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    PsiModifierList modifierList = method.getModifierList();
    int accessLevel = PsiUtil.getAccessLevel(modifierList);
    int superAccessLevel = PsiUtil.getAccessLevel(superMethod.getModifierList());
    if (accessLevel < superAccessLevel) {
      myVisitor.report(JavaErrorKinds.METHOD_INHERITANCE_WEAKER_PRIVILEGES.create(method, superMethod));
    }
  }

  private static boolean isEnumSyntheticMethod(@NotNull MethodSignature methodSignature, @NotNull Project project) {
    if (methodSignature.equals(ourValuesEnumSyntheticMethod)) return true;
    PsiType javaLangString = PsiType.getJavaLangString(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
    MethodSignature valueOfMethod = MethodSignatureUtil.createMethodSignature("valueOf", new PsiType[]{javaLangString},
                                                                              PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
    return MethodSignatureUtil.areSignaturesErasureEqual(valueOfMethod, methodSignature);
  }

  void checkDuplicateMethod(@NotNull PsiClass aClass, @NotNull PsiMethod method) {
    if (method instanceof ExternallyDefinedPsiElement) return;
    MostlySingularMultiMap<MethodSignature, PsiMethod> duplicateMethods = getDuplicateMethods(aClass);
    MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
    int methodCount = 1;
    List<PsiMethod> methods = (List<PsiMethod>)duplicateMethods.get(methodSignature);
    if (methods.size() > 1) {
      methodCount++;
    }

    if (methodCount == 1 && aClass.isEnum() && isEnumSyntheticMethod(methodSignature, aClass.getProject())) {
      methodCount++;
    }
    if (methodCount > 1) {
      myVisitor.report(JavaErrorKinds.METHOD_DUPLICATE.create(method));
    }
  }
}
