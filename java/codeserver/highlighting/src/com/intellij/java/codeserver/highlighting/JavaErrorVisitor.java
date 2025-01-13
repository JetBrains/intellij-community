// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
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
  final @NotNull ClassChecker myClassChecker = new ClassChecker(this);
  private final @NotNull GenericsChecker myGenericsChecker = new GenericsChecker(this);
  private final @NotNull MethodChecker myMethodChecker = new MethodChecker(this);
  private final @NotNull ReceiverChecker myReceiverChecker = new ReceiverChecker(this);
  private final @NotNull ExpressionChecker myExpressionChecker = new ExpressionChecker(this);
  private final @NotNull LiteralChecker myLiteralChecker = new LiteralChecker(this);
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

  @NotNull LanguageLevel languageLevel() {
    return myLanguageLevel;
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
    if (!hasErrorResults()) myAnnotationChecker.checkPackageAnnotationContainingFile(statement);
    if (!hasErrorResults()) myClassChecker.checkPackageNotAllowedInImplicitClass(statement);
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
    if (!hasErrorResults()) myClassChecker.checkAnonymousInheritFinal(expression);
    if (!hasErrorResults()) myClassChecker.checkAnonymousInheritProhibited(expression);
    if (!hasErrorResults()) myClassChecker.checkAnonymousSealedProhibited(expression);
    if (!hasErrorResults()) myExpressionChecker.checkQualifiedNew(expression, type, aClass);
  }

  @Override
  public void visitComment(@NotNull PsiComment comment) {
    super.visitComment(comment);
    if (!hasErrorResults()) checkShebangComment(comment);
    if (!hasErrorResults()) checkUnclosedComment(comment);
    if (!hasErrorResults()) checkIllegalUnicodeEscapes(comment);
  }

  @Override
  public void visitDocComment(@NotNull PsiDocComment comment) {
    if (!hasErrorResults()) checkUnclosedComment(comment);
    if (!hasErrorResults()) checkIllegalUnicodeEscapes(comment);
  }

  @Override
  public void visitFragment(@NotNull PsiFragment fragment) {
    super.visitFragment(fragment);
    checkIllegalUnicodeEscapes(fragment);
  }

  @Override
  public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
    super.visitLiteralExpression(expression);

    if (!hasErrorResults() &&
        expression.getParent() instanceof PsiCaseLabelElementList &&
        expression.textMatches(PsiKeyword.NULL)) {
      checkFeature(expression, JavaFeature.PATTERNS_IN_SWITCH);
    }

    if (!hasErrorResults()) checkIllegalUnicodeEscapes(expression);
    if (!hasErrorResults()) myLiteralChecker.getLiteralExpressionParsingError(expression);
  }

  @Override
  public void visitTypeParameterList(@NotNull PsiTypeParameterList list) {
    PsiTypeParameter[] typeParameters = list.getTypeParameters();
    if (typeParameters.length > 0) {
      checkFeature(list, JavaFeature.GENERICS);
      if (!hasErrorResults()) myGenericsChecker.checkTypeParametersList(list, typeParameters);
    }
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
  public void visitJavaFile(@NotNull PsiJavaFile file) {
    super.visitJavaFile(file);
    if (!hasErrorResults()) myClassChecker.checkImplicitClassWellFormed(file);
    if (!hasErrorResults()) myClassChecker.checkDuplicateClassesWithImplicit(file);
  }

  @Override
  public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
    super.visitClassInitializer(initializer);
    if (!hasErrorResults()) myClassChecker.checkImplicitClassMember(initializer);
    if (!hasErrorResults()) myClassChecker.checkIllegalInstanceMemberInRecord(initializer);
    if (!hasErrorResults()) myClassChecker.checkThingNotAllowedInInterface(initializer);
    if (!hasErrorResults()) myClassChecker.checkInitializersInImplicitClass(initializer);
  }

  @Override
  public void visitField(@NotNull PsiField field) {
    super.visitField(field);
    if (!hasErrorResults()) myClassChecker.checkIllegalInstanceMemberInRecord(field);
  }

  @Override
  public void visitIdentifier(@NotNull PsiIdentifier identifier) {
    PsiElement parent = identifier.getParent();
    if (parent instanceof PsiVariable variable) {
      if (variable instanceof PsiField field) {
        myClassChecker.checkImplicitClassMember(field);
      }
    }
    else if (parent instanceof PsiClass aClass) {
      if (aClass.isAnnotationType()) {
        checkFeature(identifier, JavaFeature.ANNOTATIONS);
      }
      if (!hasErrorResults()) myClassChecker.checkClassAlreadyImported(aClass);
      if (!hasErrorResults()) myClassChecker.checkClassRestrictedKeyword(identifier);
    }
    else if (parent instanceof PsiMethod method) {
      myClassChecker.checkImplicitClassMember(method);
    }
  }

  @Override
  public void visitKeyword(@NotNull PsiKeyword keyword) {
    super.visitKeyword(keyword);
    if (!hasErrorResults()) myClassChecker.checkStaticDeclarationInInnerClass(keyword);
  }

  @Override
  public void visitClass(@NotNull PsiClass aClass) {
    super.visitClass(aClass);
    if (aClass instanceof PsiSyntheticClass) return;
    if (!hasErrorResults()) myClassChecker.checkDuplicateTopLevelClass(aClass);
    if (!hasErrorResults()) myClassChecker.checkMustNotBeLocal(aClass);
    if (!hasErrorResults()) myClassChecker.checkClassAndPackageConflict(aClass);
    if (!hasErrorResults()) myClassChecker.checkPublicClassInRightFile(aClass);
    if (!hasErrorResults()) myClassChecker.checkWellFormedRecord(aClass);
    if (!hasErrorResults()) myClassChecker.checkSealedClassInheritors(aClass);
    if (!hasErrorResults()) myClassChecker.checkSealedSuper(aClass);
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
    PsiElement parent = ref.getParent();

    if (resolved != null && parent instanceof PsiReferenceList referenceList && !hasErrorResults()) {
      checkElementInReferenceList(ref, referenceList, result);
    }
    if (!hasErrorResults()) myClassChecker.checkAbstractInstantiation(ref);
    if (!hasErrorResults()) myClassChecker.checkExtendsDuplicate(ref, resolved);
    if (!hasErrorResults()) myClassChecker.checkClassExtendsForeignInnerClass(ref, resolved);
    return result;
  }

  private void checkShebangComment(@NotNull PsiComment comment) {
    if (comment.getTextOffset() != 0) return;
    if (comment.getText().startsWith("#!")) {
      VirtualFile file = PsiUtilCore.getVirtualFile(comment);
      if (file != null && "java".equals(file.getExtension())) {
        report(JavaErrorKinds.COMMENT_SHEBANG_JAVA_FILE.create(comment));
      }
    }
  }

  private void checkUnclosedComment(@NotNull PsiComment comment) {
    if (!(comment instanceof PsiDocComment) && comment.getTokenType() != JavaTokenType.C_STYLE_COMMENT) return;
    String text = comment.getText();
    if (text.startsWith("/*") && !text.endsWith("*/")) {
      report(JavaErrorKinds.COMMENT_UNCLOSED.create(comment));
    }
  }

  private void checkIllegalUnicodeEscapes(@NotNull PsiElement element) {
    LiteralChecker.parseUnicodeEscapes(element.getText(),
                                       (start, end) -> report(JavaErrorKinds.ILLEGAL_UNICODE_ESCAPE.create(element, TextRange.create(start, end))));
  }

  private void checkElementInReferenceList(@NotNull PsiJavaCodeReferenceElement ref,
                                           @NotNull PsiReferenceList referenceList,
                                           @NotNull JavaResolveResult resolveResult) {
    PsiElement resolved = resolveResult.getElement();
    PsiElement refGrandParent = referenceList.getParent();
    if (resolved instanceof PsiClass aClass) {
      if (refGrandParent instanceof PsiClass parentClass) {
        if (refGrandParent instanceof PsiTypeParameter typeParameter) {
          myGenericsChecker.checkElementInTypeParameterExtendsList(referenceList, typeParameter, resolveResult, ref);
        }
        else if (referenceList.equals(parentClass.getImplementsList()) || referenceList.equals(parentClass.getExtendsList())) {
          myClassChecker.checkExtendsClassAndImplementsInterface(referenceList, aClass, ref);
          if (!hasErrorResults() && referenceList.equals(parentClass.getExtendsList())) {
            myClassChecker.checkValueClassExtends(aClass, parentClass, ref);
          }
          if (!hasErrorResults()) {
            myClassChecker.checkCannotInheritFromFinal(aClass, ref);
          }
          if (!hasErrorResults()) {
            myClassChecker.checkExtendsProhibitedClass(aClass, parentClass, ref);
          }
          if (!hasErrorResults()) {
            // TODO: checkExtendsSealedClass
          }
          if (!hasErrorResults()) {
            myGenericsChecker.checkCannotInheritFromTypeParameter(aClass, ref);
          }
        }
      }
      else if (refGrandParent instanceof PsiMethod method && method.getThrowsList() == referenceList) {
        myMethodChecker.checkMustBeThrowable(aClass, ref);
      }
    }
    else if (refGrandParent instanceof PsiMethod method && referenceList == method.getThrowsList()) {
      report(JavaErrorKinds.METHOD_THROWS_CLASS_NAME_EXPECTED.create(ref));
    }
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
      if (!hasErrorResults()) myClassChecker.checkExtendsAllowed(list);
      if (!hasErrorResults()) myClassChecker.checkImplementsAllowed(list);
      if (!hasErrorResults()) myClassChecker.checkClassExtendsOnlyOneClass(list);
      if (!hasErrorResults()) myClassChecker.checkPermitsList(list);
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
    if (!hasErrorResults() && method.isConstructor()) {
      myClassChecker.checkThingNotAllowedInInterface(method);
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
