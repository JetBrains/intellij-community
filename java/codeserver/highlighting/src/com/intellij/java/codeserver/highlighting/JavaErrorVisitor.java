// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * An internal visitor to gather error messages for Java sources. Should not be used directly.
 * Use {@link JavaErrorCollector} instead.
 */
final class JavaErrorVisitor extends JavaElementVisitor {
  private final @NotNull Consumer<JavaCompilationError<?, ?>> myErrorConsumer;
  private final @NotNull Project myProject;
  private final @NotNull PsiFile myFile;
  private final @NotNull LanguageLevel myLanguageLevel;
  private final @NotNull AnnotationChecker myAnnotationChecker = new AnnotationChecker(this);
  private final @NotNull ClassChecker myClassChecker = new ClassChecker(this);
  private final @NotNull MethodChecker myMethodChecker = new MethodChecker(this);
  private final @NotNull ReceiverChecker myReceiverChecker = new ReceiverChecker(this);
  private boolean myHasError; // true if myHolder.add() was called with HighlightInfo of >=ERROR severity. On each .visit(PsiElement) call this flag is reset. Useful to determine whether the error was already reported while visiting this PsiElement.

  JavaErrorVisitor(@NotNull PsiFile file, @NotNull Consumer<JavaCompilationError<?, ?>> consumer) {
    myFile = file;
    myProject = file.getProject();
    myLanguageLevel = PsiUtil.getLanguageLevel(file);
    myErrorConsumer = consumer;
  }

  void report(@NotNull JavaCompilationError<?, ?> error) {
    myErrorConsumer.accept(error);
    myHasError = true;
  }
  
  @Contract(pure = true)
  boolean isApplicable(@NotNull JavaFeature feature) {
    return feature.isSufficient(myLanguageLevel);
  }

  @NotNull PsiFile file() {
    return myFile;
  }

  @Contract(pure = true)
  boolean hasErrorResults() {
    return myHasError;
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    super.visitElement(element);
    myHasError = false;
  }

  @Override
  public void visitAnnotation(@NotNull PsiAnnotation annotation) {
    super.visitAnnotation(annotation);
    if (!hasErrorResults()) checkFeature(annotation, JavaFeature.ANNOTATIONS);
    myAnnotationChecker.checkAnnotation(annotation);
  }
  
  @Override
  public void visitPackageStatement(@NotNull PsiPackageStatement statement) {
    super.visitPackageStatement(statement);
    myAnnotationChecker.checkPackageAnnotationContainingFile(statement);
  }

  @Override
  public void visitReceiverParameter(@NotNull PsiReceiverParameter parameter) {
    super.visitReceiverParameter(parameter);
    myReceiverChecker.checkReceiver(parameter);
  }

  @Override
  public void visitNameValuePair(@NotNull PsiNameValuePair pair) {
    super.visitNameValuePair(pair);
    myAnnotationChecker.checkNameValuePair(pair);
  }

  @Override
  public void visitEnumConstant(@NotNull PsiEnumConstant enumConstant) {
    super.visitEnumConstant(enumConstant);
    if (!hasErrorResults()) myClassChecker.checkEnumWithAbstractMethods(enumConstant);
  }

  @Override
  public void visitNewExpression(@NotNull PsiNewExpression expression) {
    super.visitNewExpression(expression);
    PsiType type = expression.getType();
    PsiClass aClass = PsiUtil.resolveClassInType(type);
    if (aClass != null && !hasErrorResults()) myClassChecker.checkIllegalInstantiation(aClass, expression);
  }

  @Override
  public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
    super.visitMethodReferenceExpression(expression);
    checkFeature(expression, JavaFeature.METHOD_REFERENCES);
    PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
    if (functionalInterfaceType != null && !PsiTypesUtil.allTypeParametersResolved(expression, functionalInterfaceType)) return;

    JavaResolveResult result;
    JavaResolveResult[] results;
    try {
      results = expression.multiResolve(true);
      result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
    }
    catch (IndexNotReadyException e) {
      return;
    }
    if (!hasErrorResults()) {
      boolean resolvedButNonApplicable = results.length == 1 && results[0] instanceof MethodCandidateInfo methodInfo &&
                                         !methodInfo.isApplicable() &&
                                         functionalInterfaceType != null;
      if (results.length != 1 || resolvedButNonApplicable) {
        if (expression.isConstructor()) {
          PsiClass containingClass = PsiMethodReferenceUtil.getQualifierResolveResult(expression).getContainingClass();

          if (containingClass != null) {
            myClassChecker.checkIllegalInstantiation(containingClass, expression);
          }
        }
      }
    }
  }

  @Override
  public void visitModifierList(@NotNull PsiModifierList list) {
    super.visitModifierList(list);
    PsiElement parent = list.getParent();
    if (parent instanceof PsiMethod method) {

    }
    else if (parent instanceof PsiClass aClass) {
      if (!hasErrorResults()) myClassChecker.checkDuplicateNestedClass(aClass);
      if (!hasErrorResults() && !(aClass instanceof PsiAnonymousClass)) {
        /* anonymous class is highlighted in HighlightClassUtil.checkAbstractInstantiation()*/
        myClassChecker.checkClassMustBeAbstract(aClass);
      }
      if (!hasErrorResults()) {
        //myClassChecker.checkClassDoesNotCallSuperConstructorOrHandleExceptions(aClass, getResolveHelper(getProject()));
      }
      //if (!hasErrorResults()) add(HighlightMethodUtil.checkOverrideEquivalentInheritedMethods(aClass, myFile, myLanguageLevel));
      if (!hasErrorResults()) {
        //GenericsHighlightUtil.computeOverrideEquivalentMethodErrors(aClass, myOverrideEquivalentMethodsVisitedClasses, myOverrideEquivalentMethodsErrors);
        //myErrorSink.accept(myOverrideEquivalentMethodsErrors.get(aClass));
      }
      if (!hasErrorResults()) myClassChecker.checkCyclicInheritance(aClass);
    }
  }
  
  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
    JavaResolveResult resultForIncompleteCode = doVisitReferenceElement(expression);
    if (!hasErrorResults()) {
      visitExpression(expression);
      if (hasErrorResults()) return;
    }
  }

  @Override
  public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement ref) {
    JavaResolveResult result = ref instanceof PsiExpression ? resolveOptimised(ref, myFile) : doVisitReferenceElement(ref);
    if (result != null) {
      PsiElement resolved = result.getElement();
    }
  }

  private JavaResolveResult doVisitReferenceElement(@NotNull PsiJavaCodeReferenceElement ref) {
    JavaResolveResult result = resolveOptimised(ref, myFile);
    if (result == null) return null;

    PsiElement resolved = result.getElement();

    if (!hasErrorResults()) myClassChecker.checkAbstractInstantiation(ref);
    if (!hasErrorResults()) myClassChecker.checkExtendsDuplicate(ref, resolved);
    if (!hasErrorResults()) myClassChecker.checkClassExtendsForeignInnerClass(ref, resolved);
    return result;
  }

  static @Nullable JavaResolveResult resolveOptimised(@NotNull PsiJavaCodeReferenceElement ref, @NotNull PsiFile containingFile) {
    try {
      if (ref instanceof PsiReferenceExpressionImpl) {
        PsiReferenceExpressionImpl.OurGenericsResolver resolver = PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE;
        JavaResolveResult[] results = JavaResolveUtil.resolveWithContainingFile(ref, resolver, true, true, containingFile);
        return results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
      }
      return ref.advancedResolve(true);
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  @Override
  public void visitReferenceList(@NotNull PsiReferenceList list) {
    super.visitReferenceList(list);
    if (list.getFirstChild() == null) return;
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiTypeParameter)) {
      myAnnotationChecker.checkAnnotationDeclaration(parent, list);
    }
  }

  @Override
  public void visitExpression(@NotNull PsiExpression expression) {
    super.visitExpression(expression);
    if (!hasErrorResults()) myAnnotationChecker.checkConstantExpression(expression);
  }

  @Override
  public void visitAnnotationArrayInitializer(@NotNull PsiArrayInitializerMemberValue initializer) {
    super.visitAnnotationArrayInitializer(initializer);
    myAnnotationChecker.checkArrayInitializer(initializer);
  }

  @Override
  public void visitMethod(@NotNull PsiMethod method) {
    super.visitMethod(method);
    PsiClass aClass = method.getContainingClass();
    if (!hasErrorResults() &&
        (method.hasModifierProperty(PsiModifier.DEFAULT) ||
         aClass != null && aClass.isInterface() && method.hasModifierProperty(PsiModifier.STATIC))) {
      checkFeature(method, JavaFeature.EXTENSION_METHODS);
    }
    if (!hasErrorResults() && aClass != null) {
      myMethodChecker.checkDuplicateMethod(aClass, method);
    }
  }

  @Override
  public void visitAnnotationMethod(@NotNull PsiAnnotationMethod method) {
    PsiType returnType = method.getReturnType();
    PsiAnnotationMemberValue value = method.getDefaultValue();
    if (returnType != null && value != null) {
      myAnnotationChecker.checkMemberValueType(value, returnType, method);
    }
    PsiTypeElement typeElement = method.getReturnTypeElement();
    if (typeElement != null) {
      myAnnotationChecker.checkValidAnnotationType(returnType, typeElement);
    }

    PsiClass aClass = method.getContainingClass();
    if (typeElement != null && aClass != null) {
      myAnnotationChecker.checkCyclicMemberType(typeElement, aClass);
    }

    myAnnotationChecker.checkClashesWithSuperMethods(method);

    if (!hasErrorResults() && aClass != null) {
      myMethodChecker.checkDuplicateMethod(aClass, method);
    }
  }

  void checkFeature(@NotNull PsiElement element, @NotNull JavaFeature feature) {
    if (!feature.isSufficient(myLanguageLevel)) {
      report(JavaErrorKinds.UNSUPPORTED_FEATURE.create(element, feature));
    }
  }
}
