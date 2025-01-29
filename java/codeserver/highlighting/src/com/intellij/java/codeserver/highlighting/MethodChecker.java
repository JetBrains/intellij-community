// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeErrorContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.*;
import com.intellij.util.containers.MostlySingularMultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class MethodChecker {
  private final @NotNull JavaErrorVisitor myVisitor;
  private final @NotNull Map<PsiClass, MostlySingularMultiMap<MethodSignature, PsiMethod>> myDuplicateMethods = new HashMap<>();
  private static final TokenSet BRACKET_TOKENS = TokenSet.create(JavaTokenType.LBRACKET, JavaTokenType.RBRACKET);
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

  void checkMustBeThrowable(@NotNull PsiClass aClass, @NotNull PsiJavaCodeReferenceElement context) {
    if (!InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_THROWABLE)) {
      PsiElementFactory factory = myVisitor.factory();
      PsiClassType type = factory.createType(aClass);
      PsiClassType throwable = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_THROWABLE, context.getResolveScope());
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
      myVisitor.report(errorKind.create(method, new JavaErrorKinds.OverrideClashContext(method, superMethod)));
      return;
    }

    if (isMethodStatic) {
      if (superClass.isInterface()) return;
      checkIsWeaker(method, method, superMethod);
      if (!myVisitor.hasErrorResults()) checkSuperMethodIsFinal(method, superMethod);
    }
  }

  private void checkSuperMethodIsFinal(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    // strange things happen when super method is from Object and method from interface
    if (superMethod.hasModifierProperty(PsiModifier.FINAL)) {
      myVisitor.report(JavaErrorKinds.METHOD_OVERRIDES_FINAL.create(method, superMethod));
    }
  }

  private void checkIsWeaker(@NotNull PsiMember anchor, @NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    PsiModifierList modifierList = method.getModifierList();
    int accessLevel = PsiUtil.getAccessLevel(modifierList);
    int superAccessLevel = PsiUtil.getAccessLevel(superMethod.getModifierList());
    if (accessLevel < superAccessLevel) {
      myVisitor.report(JavaErrorKinds.METHOD_INHERITANCE_WEAKER_PRIVILEGES.create(
        anchor, new JavaErrorKinds.OverrideClashContext(method, superMethod)));
    }
  }

  void checkOverrideEquivalentInheritedMethods(@NotNull PsiClass aClass) {
    Collection<HierarchicalMethodSignature> visibleSignatures = aClass.getVisibleSignatures();
    if (aClass.getImplementsListTypes().length == 0 && aClass.getExtendsListTypes().length == 0) {
      // optimization: do not analyze unrelated methods from Object: in case of no inheritance they can't conflict
      return;
    }
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(aClass.getProject()).getResolveHelper();

    for (HierarchicalMethodSignature signature : visibleSignatures) {
      PsiMethod method = signature.getMethod();
      if (!resolveHelper.isAccessible(method, aClass, null)) continue;
      List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();

      boolean allAbstracts = method.hasModifierProperty(PsiModifier.ABSTRACT);
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || aClass.equals(containingClass)) continue; //to be checked at method level

      if (aClass.isInterface() && !containingClass.isInterface()) continue;
      if (allAbstracts) {
        superSignatures = new ArrayList<>(superSignatures);
        superSignatures.add(0, signature);
        checkInterfaceInheritedMethodsReturnTypes(aClass, superSignatures);
      }
      else {
        checkMethodIncompatibleReturnType(aClass, signature, superSignatures);
      }

      if (method.hasModifierProperty(PsiModifier.STATIC) &&
          //jsl 8, chapter 9.4.1
          //chapter 8.4.8.2 speaks about a class that "declares or inherits a static method",
          // at the same time the rule from chapter 9.4.1 speaks only about an interface that "declares a static method"
          //There is no point to add java version check, because static methods in interfaces are allowed from java 8 too.
          (!aClass.isInterface() ||
           aClass.getManager().areElementsEquivalent(aClass, method.getContainingClass()))) {
        for (HierarchicalMethodSignature superSignature : superSignatures) {
          PsiMethod superMethod = superSignature.getMethod();
          if (!superMethod.hasModifierProperty(PsiModifier.STATIC)) {
            myVisitor.report(JavaErrorKinds.METHOD_STATIC_OVERRIDES_INSTANCE.create(
              aClass, new JavaErrorKinds.OverrideClashContext(method, superMethod)));
            return;
          }
        }
        continue;
      }

      if (!myVisitor.hasErrorResults()) {
        checkMethodIncompatibleThrows(aClass, signature, superSignatures, aClass);
      }

      if (!myVisitor.hasErrorResults()) {
        checkMethodWeakerPrivileges(aClass, signature, superSignatures);
      }
    }
  }

  void checkMethodWeakerPrivileges(@NotNull PsiMember anchor,
                                   @NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                   @NotNull List<? extends HierarchicalMethodSignature> superMethodSignatures) {
    PsiMethod method = methodSignature.getMethod();
    PsiModifierList modifierList = method.getModifierList();
    if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) return;
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      if (method.hasModifierProperty(PsiModifier.ABSTRACT) && !MethodSignatureUtil.isSuperMethod(superMethod, method)) continue;
      if (!PsiUtil.isAccessible(myVisitor.project(), superMethod, method, null)) continue;
      if (anchor instanceof PsiClass && MethodSignatureUtil.isSuperMethod(superMethod, method)) continue;
      checkIsWeaker(anchor, method, superMethod);
    }
  }

  void checkMethodIncompatibleThrows(@NotNull PsiMember anchor,
                                     @NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                     @NotNull List<? extends HierarchicalMethodSignature> superMethodSignatures,
                                     @NotNull PsiClass analyzedClass) {
    PsiMethod method = methodSignature.getMethod();
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return;
    PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(aClass, analyzedClass, PsiSubstitutor.EMPTY);
    PsiClassType[] exceptions = method.getThrowsList().getReferencedTypes();
    PsiJavaCodeReferenceElement[] referenceElements = anchor == method ? method.getThrowsList().getReferenceElements() : null;
    List<PsiJavaCodeReferenceElement> exceptionContexts = new ArrayList<>();
    List<PsiClassType> checkedExceptions = new ArrayList<>();
    for (int i = 0; i < exceptions.length; i++) {
      PsiClassType exception = exceptions[i];
      if (!ExceptionUtil.isUncheckedException(exception)) {
        checkedExceptions.add(exception);
        if (referenceElements != null && i < referenceElements.length) {
          PsiJavaCodeReferenceElement exceptionRef = referenceElements[i];
          exceptionContexts.add(exceptionRef);
        }
      }
    }
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      int index = getExtraExceptionNum(methodSignature, superMethodSignature, checkedExceptions, superSubstitutor);
      if (index != -1) {
        if (aClass.isInterface()) {
          PsiClass superContainingClass = superMethod.getContainingClass();
          if (superContainingClass != null && !superContainingClass.isInterface()) continue;
          if (superContainingClass != null && !aClass.isInheritor(superContainingClass, true)) continue;
        }
        PsiClassType exception = checkedExceptions.get(index);
        myVisitor.report(JavaErrorKinds.METHOD_INHERITANCE_CLASH_DOES_NOT_THROW.create(
          anchor,
          new JavaErrorKinds.IncompatibleOverrideExceptionContext(method, superMethod, exception, 
                                                                  exceptionContexts.isEmpty() ? null : exceptionContexts.get(index))));
        return;
      }
    }
  }

  void checkMethodIncompatibleReturnType(@NotNull PsiMember anchor, @NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                         @NotNull List<? extends HierarchicalMethodSignature> superMethodSignatures) {
    PsiMethod method = methodSignature.getMethod();
    PsiType returnType = methodSignature.getSubstitutor().substitute(method.getReturnType());
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return;
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      PsiType declaredReturnType = superMethod.getReturnType();
      PsiType superReturnType = declaredReturnType;
      if (superMethodSignature.isRaw()) superReturnType = TypeConversionUtil.erasure(declaredReturnType);
      if (returnType == null || superReturnType == null || method == superMethod) continue;
      PsiClass superClass = superMethod.getContainingClass();
      if (superClass == null) continue;
      checkSuperMethodSignature(anchor, superMethod, superMethodSignature, superReturnType, method, methodSignature, returnType);
    }
  }

  private void checkSuperMethodSignature(@NotNull PsiMember anchor,
                                         @NotNull PsiMethod superMethod,
                                         @NotNull MethodSignatureBackedByPsiMethod superMethodSignature,
                                         @NotNull PsiType superReturnType,
                                         @NotNull PsiMethod method,
                                         @NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                         @NotNull PsiType returnType) {
    PsiClass superContainingClass = superMethod.getContainingClass();
    if (superContainingClass != null &&
        CommonClassNames.JAVA_LANG_OBJECT.equals(superContainingClass.getQualifiedName()) &&
        !superMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
      PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && containingClass.isInterface() && !superContainingClass.isInterface()) {
        return;
      }
    }

    PsiType substitutedSuperReturnType;
    // Important: we should use the language level of the file where the method is declared,
    // not the language level of the current file, so myVisitor.isApplicable() doesn't work here. 
    boolean hasGenerics = PsiUtil.isAvailable(JavaFeature.GENERICS, method);
    if (hasGenerics && !superMethodSignature.isRaw() && superMethodSignature.equals(methodSignature)) { //see 8.4.5
      PsiSubstitutor unifyingSubstitutor = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature,
                                                                                                  superMethodSignature);
      substitutedSuperReturnType = unifyingSubstitutor == null
                                   ? superReturnType
                                   : unifyingSubstitutor.substitute(superReturnType);
    }
    else {
      substitutedSuperReturnType = TypeConversionUtil.erasure(superMethodSignature.getSubstitutor().substitute(superReturnType));
    }

    if (returnType.equals(substitutedSuperReturnType)) return;
    if (!(returnType instanceof PsiPrimitiveType) && substitutedSuperReturnType.getDeepComponentType() instanceof PsiClassType) {
      if (hasGenerics && LambdaUtil.performWithSubstitutedParameterBounds(
        methodSignature.getTypeParameters(), methodSignature.getSubstitutor(),
        () -> TypeConversionUtil.isAssignable(substitutedSuperReturnType, returnType))) {
        return;
      }
    }

    myVisitor.report(JavaErrorKinds.METHOD_INHERITANCE_CLASH_INCOMPATIBLE_RETURN_TYPES.create(
      anchor, new JavaErrorKinds.IncompatibleOverrideReturnTypeContext(method, returnType, superMethod, substitutedSuperReturnType)));
  }

  private void checkInterfaceInheritedMethodsReturnTypes(@NotNull PsiClass aClass,
                                                         @NotNull List<HierarchicalMethodSignature> superMethodSignatures) {
    if (superMethodSignatures.size() < 2) return;
    MethodSignatureBackedByPsiMethod[] returnTypeSubstitutable = {superMethodSignatures.get(0)};
    for (int i = 1; i < superMethodSignatures.size(); i++) {
      PsiMethod currentMethod = returnTypeSubstitutable[0].getMethod();
      PsiType currentType = returnTypeSubstitutable[0].getSubstitutor().substitute(currentMethod.getReturnType());

      MethodSignatureBackedByPsiMethod otherSuperSignature = superMethodSignatures.get(i);
      PsiMethod otherSuperMethod = otherSuperSignature.getMethod();
      PsiSubstitutor otherSubstitutor = otherSuperSignature.getSubstitutor();
      PsiType otherSuperReturnType = otherSubstitutor.substitute(otherSuperMethod.getReturnType());
      PsiSubstitutor unifyingSubstitutor = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(returnTypeSubstitutable[0],
                                                                                                  otherSuperSignature);
      if (unifyingSubstitutor != null) {
        otherSuperReturnType = unifyingSubstitutor.substitute(otherSuperReturnType);
        currentType = unifyingSubstitutor.substitute(currentType);
      }

      if (otherSuperReturnType == null || currentType == null || otherSuperReturnType.equals(currentType)) continue;
      PsiType otherReturnType = otherSuperReturnType;
      PsiType curType = currentType;
      LambdaUtil.performWithSubstitutedParameterBounds(otherSuperMethod.getTypeParameters(), otherSubstitutor, () -> {
        if (myVisitor.languageLevel().isAtLeast(LanguageLevel.JDK_1_5)) {
          //http://docs.oracle.com/javase/specs/jls/se7/html/jls-8.html#jls-8.4.8 Example 8.1.5-3
          if (!(otherReturnType instanceof PsiPrimitiveType || curType instanceof PsiPrimitiveType)) {
            if (otherReturnType.isAssignableFrom(curType)) return null;
            if (curType.isAssignableFrom(otherReturnType)) {
              returnTypeSubstitutable[0] = otherSuperSignature;
              return null;
            }
          }
          if (otherSuperMethod.getTypeParameters().length > 0 && JavaGenericsUtil.isRawToGeneric(otherReturnType, curType)) return null;
        }
        myVisitor.report(JavaErrorKinds.METHOD_INHERITANCE_CLASH_UNRELATED_RETURN_TYPES
                           .create(aClass, new JavaErrorKinds.OverrideClashContext(currentMethod, otherSuperMethod)));
        return null;
      });
    }
  }

  void checkVarArgParameterWellFormed(@NotNull PsiParameter parameter) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiMethod psiMethod) {
      PsiParameter[] params = psiMethod.getParameterList().getParameters();
      if (params[params.length - 1] != parameter) {
        myVisitor.report(JavaErrorKinds.VARARG_NOT_LAST_PARAMETER.create(parameter));
        return;
      }
    }
    TextRange range = getCStyleDeclarationRange(parameter);
    if (range != null) {
      myVisitor.report(JavaErrorKinds.VARARG_CSTYLE_DECLARATION.create(parameter, range));
    }
  }

  void checkConstructorName(PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    if (aClass != null) {
      String className = aClass instanceof PsiAnonymousClass ? null : aClass.getName();
      if (className == null || !Comparing.strEqual(method.getName(), className)) {
        myVisitor.report(JavaErrorKinds.METHOD_MISSING_RETURN_TYPE.create(method, className));
      }
    }
  }

  static @Nullable TextRange getCStyleDeclarationRange(@NotNull PsiVariable variable) {
    PsiIdentifier identifier = variable.getNameIdentifier();
    TextRange range = null;
    if (identifier != null) {
      PsiElement start = null;
      PsiElement end = null;
      for (PsiElement element = identifier.getNextSibling(); element != null; element = element.getNextSibling()) {
        if (PsiUtil.isJavaToken(element, BRACKET_TOKENS)) {
          if (start == null) start = element;
          end = element;
        }
      }
      if (start != null) {
        range = TextRange.create(start.getTextRange().getStartOffset(), end.getTextRange().getEndOffset())
          .shiftLeft(variable.getTextRange().getStartOffset());
      }
    }
    return range;
  }

  void checkMethodOverridesFinal(@NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                 @NotNull List<? extends HierarchicalMethodSignature> superMethodSignatures) {
    PsiMethod method = methodSignature.getMethod();
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      checkSuperMethodIsFinal(method, superMethod);
      if (myVisitor.hasErrorResults()) return;
    }
  }

  // return number of exception  which was not declared in super method or -1
  private static int getExtraExceptionNum(@NotNull MethodSignature methodSignature,
                                          @NotNull MethodSignatureBackedByPsiMethod superSignature,
                                          @NotNull List<? extends PsiClassType> checkedExceptions,
                                          @NotNull PsiSubstitutor substitutorForDerivedClass) {
    PsiMethod superMethod = superSignature.getMethod();
    PsiSubstitutor substitutorForMethod = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature, superSignature);
    for (int i = 0; i < checkedExceptions.size(); i++) {
      PsiClassType checkedEx = checkedExceptions.get(i);
      PsiType substituted =
        substitutorForMethod == null ? TypeConversionUtil.erasure(checkedEx) : substitutorForMethod.substitute(checkedEx);
      PsiType exception = substitutorForDerivedClass.substitute(substituted);
      if (!isMethodThrows(superMethod, substitutorForMethod, exception, substitutorForDerivedClass)) {
        return i;
      }
    }
    return -1;
  }

  private static boolean isMethodThrows(@NotNull PsiMethod method,
                                        @Nullable PsiSubstitutor substitutorForMethod,
                                        @NotNull PsiType exception,
                                        @NotNull PsiSubstitutor substitutorForDerivedClass) {
    PsiClassType[] thrownExceptions = method.getThrowsList().getReferencedTypes();
    for (PsiClassType thrownException1 : thrownExceptions) {
      PsiType thrownException =
        substitutorForMethod != null ? substitutorForMethod.substitute(thrownException1) : TypeConversionUtil.erasure(thrownException1);
      thrownException = substitutorForDerivedClass.substitute(thrownException);
      if (TypeConversionUtil.isAssignable(thrownException, exception)) return true;
    }
    return false;
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
