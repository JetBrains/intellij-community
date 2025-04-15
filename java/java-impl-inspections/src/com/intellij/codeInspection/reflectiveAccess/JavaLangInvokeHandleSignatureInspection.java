// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reflectiveAccess;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInspection.*;
import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.codeInspection.reflectiveAccess.JavaLangReflectVarHandleInvocationChecker.ARRAY_ELEMENT_VAR_HANDLE;
import static com.intellij.codeInspection.reflectiveAccess.JavaLangReflectVarHandleInvocationChecker.JAVA_LANG_INVOKE_METHOD_HANDLES;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

public final class JavaLangInvokeHandleSignatureInspection extends AbstractBaseJavaLocalInspectionTool {
  public static final Key<ReflectiveSignature> DEFAULT_SIGNATURE = Key.create("DEFAULT_SIGNATURE");
  public static final Key<List<LookupElement>> POSSIBLE_SIGNATURES = Key.create("POSSIBLE_SIGNATURES");

  static final @Unmodifiable Set<String> KNOWN_METHOD_NAMES =
    ContainerUtil.union(Arrays.asList(HANDLE_FACTORY_METHOD_NAMES), Collections.singletonList(FIND_CONSTRUCTOR));

  private interface CallChecker {
    boolean checkCall(@NotNull PsiMethodCallExpression callExpression, @NotNull ProblemsHolder holder);
  }

  private static final CallChecker[] CALL_CHECKERS = {
    JavaLangInvokeHandleSignatureInspection::checkHandlerFactoryCall,
    JavaLangReflectHandleInvocationChecker::checkMethodHandleInvocation,
    JavaLangReflectVarHandleInvocationChecker::checkVarHandleAccess,
  };

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression callExpression) {
        super.visitMethodCallExpression(callExpression);

        for (CallChecker checker : CALL_CHECKERS) {
          if (checker.checkCall(callExpression, holder)) return;
        }
      }
    };
  }

  private static boolean checkHandlerFactoryCall(@NotNull PsiMethodCallExpression callExpression, @NotNull ProblemsHolder holder) {
    final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
    final String methodName = methodExpression.getReferenceName();
    if (methodName != null && KNOWN_METHOD_NAMES.contains(methodName)) {
      final PsiMethod method = callExpression.resolveMethod();
      if (method != null && isClassWithName(method.getContainingClass(), JAVA_LANG_INVOKE_METHOD_HANDLES_LOOKUP)) {
        final PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
        checkHandleFactory(methodName, methodExpression, arguments, holder);
      }
      return true;
    }
    if (isCallToMethod(callExpression, JAVA_LANG_INVOKE_METHOD_HANDLES, ARRAY_ELEMENT_VAR_HANDLE)) {
      checkArrayElementVarHandle(callExpression, holder);
      return true;
    }
    return false;
  }

  private static void checkHandleFactory(@NotNull String factoryMethodName,
                                         @NotNull PsiReferenceExpression factoryMethodExpression,
                                         PsiExpression @NotNull [] arguments,
                                         @NotNull ProblemsHolder holder) {
    if (arguments.length == 2) {
      if (FIND_CONSTRUCTOR.equals(factoryMethodName)) {
        final ReflectiveClass ownerClass = getReflectiveClass(arguments[0]);
        if (ownerClass != null) {
          final PsiExpression typeExpression = PsiUtil.skipParenthesizedExprDown(arguments[1]);
          if (typeExpression == null) return;
          checkConstructor(ownerClass, typeExpression, holder);
        }
      }
    }
    else if (arguments.length >= 3) {
      final ReflectiveClass ownerClass = getReflectiveClass(arguments[0]);
      if (ownerClass != null) {
        final PsiExpression nameExpression = PsiUtil.skipParenthesizedExprDown(arguments[1]);
        final PsiExpression nameDefinition = findDefinition(nameExpression);
        final String memberName = computeConstantExpression(nameDefinition, String.class);
        if (!StringUtil.isEmpty(memberName)) {
          final PsiExpression typeExpression = PsiUtil.skipParenthesizedExprDown(arguments[2]);
          if (typeExpression == null) return;

          switch (factoryMethodName) {
            case FIND_GETTER, FIND_SETTER, FIND_VAR_HANDLE ->
              checkField(ownerClass, memberName, nameExpression, typeExpression, false, factoryMethodExpression, holder);
            case FIND_STATIC_GETTER, FIND_STATIC_SETTER, FIND_STATIC_VAR_HANDLE ->
              checkField(ownerClass, memberName, nameExpression, typeExpression, true, factoryMethodExpression, holder);
            case FIND_VIRTUAL ->
              checkMethod(ownerClass, memberName, nameExpression, typeExpression, false, true, factoryMethodExpression, holder);
            case FIND_STATIC ->
              checkMethod(ownerClass, memberName, nameExpression, typeExpression, true, true, factoryMethodExpression, holder);
            case FIND_SPECIAL -> {
              checkMethod(ownerClass, memberName, nameExpression, typeExpression, false, false, factoryMethodExpression, holder);
              if (arguments.length > 3) {
                checkSpecial(ownerClass, arguments[3], holder);
              }
            }
          }
        }
      }
    }
  }

  private static void checkConstructor(@NotNull ReflectiveClass ownerClass,
                                       @NotNull PsiExpression constructorTypeExpression,
                                       @NotNull ProblemsHolder holder) {
    if (!ownerClass.isExact()) return;
    final ReflectiveSignature constructorSignature = composeMethodSignature(constructorTypeExpression);
    if (constructorSignature != null) {
      final List<PsiMethod> constructors = ContainerUtil.filter(ownerClass.getPsiClass().getMethods(), PsiMethod::isConstructor);
      List<ReflectiveSignature> validSignatures = null;
      if (constructors.isEmpty()) {
        if (!constructorSignature.equals(ReflectiveSignature.NO_ARGUMENT_CONSTRUCTOR_SIGNATURE)) {
          validSignatures = Collections.singletonList(ReflectiveSignature.NO_ARGUMENT_CONSTRUCTOR_SIGNATURE);
        }
      }
      else if (findMethodBySignature(constructors, constructorSignature).isEmpty()) {
        validSignatures = constructors.stream()
          .map(JavaReflectionReferenceUtil::getMethodSignature)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
      }
      if (validSignatures != null) {
        final String declarationText = getConstructorDeclarationText(ownerClass, constructorSignature);
        if (declarationText != null) {
          LocalQuickFix fix = null;
          final String ownerClassName = ownerClass.getPsiClass().getName();
          if (ownerClassName != null) {
            fix = ReplaceSignatureQuickFix
              .createFix(constructorTypeExpression, ownerClassName, validSignatures, true, holder.isOnTheFly());
          }
          holder.registerProblem(constructorTypeExpression, JavaErrorBundle.message("cannot.resolve.constructor", declarationText),
                                 LocalQuickFix.notNullElements(fix));
        }
      }
    }
  }

  private static void checkField(@NotNull ReflectiveClass ownerClass,
                                 @NotNull String fieldName,
                                 @NotNull PsiExpression fieldNameExpression,
                                 @NotNull PsiExpression fieldTypeExpression,
                                 boolean isStaticExpected,
                                 @NotNull PsiReferenceExpression factoryMethodExpression,
                                 @NotNull ProblemsHolder holder) {
    if (!ownerClass.isExact()) return;
    final PsiField field = ownerClass.getPsiClass().findFieldByName(fieldName, true);
    if (field == null) {
      holder.registerProblem(fieldNameExpression, JavaBundle.message("inspection.handle.signature.field.cannot.resolve", fieldName));
      return;
    }

    if (field.hasModifierProperty(PsiModifier.STATIC) != isStaticExpected) {
      final String factoryMethodName = factoryMethodExpression.getReferenceName();
      final PsiElement factoryMethodNameElement = factoryMethodExpression.getReferenceNameElement();
      if (factoryMethodName != null && factoryMethodNameElement != null) {
        final LocalQuickFix fix = SwitchStaticnessQuickFix.createFix(factoryMethodName, isStaticExpected);
        final String message = JavaBundle.message(
          isStaticExpected ? "inspection.handle.signature.field.not.static" : "inspection.handle.signature.field.static", fieldName);
        holder.registerProblem(factoryMethodNameElement, message, LocalQuickFix.notNullElements(fix));
        return;
      }
    }

    final ReflectiveType reflectiveType = getReflectiveType(fieldTypeExpression);
    if (reflectiveType != null && !reflectiveType.isEqualTo(field.getType())) {
      final String expectedTypeText = getTypeText(field.getType());
      final String message = JavaBundle.message("inspection.handle.signature.field.type", fieldName, expectedTypeText);
      holder.registerProblem(fieldTypeExpression, message, new FieldTypeQuickFix(expectedTypeText));
    }
  }

  private static void checkMethod(@NotNull ReflectiveClass ownerClass,
                                  @NotNull String methodName,
                                  @NotNull PsiExpression methodNameExpression,
                                  @NotNull PsiExpression methodTypeExpression,
                                  boolean isStaticExpected,
                                  boolean isAbstractAllowed,
                                  @NotNull PsiReferenceExpression factoryMethodExpression,
                                  @NotNull ProblemsHolder holder) {
    if (!ownerClass.isExact()) return;
    final PsiMethod[] methods = ownerClass.getPsiClass().findMethodsByName(methodName, true);
    if (methods.length == 0) {
      holder.registerProblem(methodNameExpression, JavaErrorBundle.message("cannot.resolve.method", methodName));
      return;
    }

    final List<PsiMethod> filteredMethods =
      ContainerUtil.filter(methods, method -> method.hasModifierProperty(PsiModifier.STATIC) == isStaticExpected);
    if (filteredMethods.isEmpty()) {
      final String factoryMethodName = factoryMethodExpression.getReferenceName();
      final PsiElement factoryMethodNameElement = factoryMethodExpression.getReferenceNameElement();
      if (factoryMethodName != null && factoryMethodNameElement != null) {
        final LocalQuickFix fix = SwitchStaticnessQuickFix.createFix(factoryMethodName, isStaticExpected);
        final String message = JavaBundle.message(
          isStaticExpected ? "inspection.handle.signature.method.not.static" : "inspection.handle.signature.method.static", methodName);
        holder.registerProblem(factoryMethodNameElement, message, LocalQuickFix.notNullElements(fix));
        return;
      }
    }
    PsiMethod onlyMethod = ContainerUtil.getOnlyItem(filteredMethods);
    if (onlyMethod != null && AnnotationUtil.isAnnotated(onlyMethod, CommonClassNames.JAVA_LANG_INVOKE_MH_POLYMORPHIC, 0)) {
      return;
    }

    final ReflectiveSignature methodSignature = composeMethodSignature(methodTypeExpression);
    if (methodSignature == null) return;

    final List<PsiMethod> matchingMethods = findMethodBySignature(filteredMethods, methodSignature);
    if (matchingMethods.isEmpty()) {
      final String declarationText = getMethodDeclarationText(methodName, methodSignature);
      final List<ReflectiveSignature> validSignatures = filteredMethods.stream()
        .map(JavaReflectionReferenceUtil::getMethodSignature)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
      final LocalQuickFix fix =
        ReplaceSignatureQuickFix.createFix(methodTypeExpression, methodName, validSignatures, false, holder.isOnTheFly());
      holder.registerProblem(methodTypeExpression, JavaErrorBundle.message("cannot.resolve.method", declarationText),
                             LocalQuickFix.notNullElements(fix));
      return;
    }
    if (!isAbstractAllowed) {
      final boolean allAbstract = ContainerUtil.and(matchingMethods, method -> method.hasModifierProperty(PsiModifier.ABSTRACT));
      if (allAbstract) {
        final String className = ownerClass.getPsiClass().getQualifiedName();
        if (className != null) {
          holder.registerProblem(methodNameExpression,
                                 JavaBundle.message("inspection.handle.signature.method.abstract", methodName, className));
        }
      }
    }
  }

  private static void checkSpecial(@NotNull ReflectiveClass ownerClass,
                                   @NotNull PsiExpression callerClassExpression,
                                   @NotNull ProblemsHolder holder) {
    final ReflectiveClass callerClass = getReflectiveClass(callerClassExpression);
    if (callerClass != null && callerClass.isExact()) {
      final PsiClass caller = callerClass.getPsiClass();
      final PsiClass owner = ownerClass.getPsiClass();
      if (!InheritanceUtil.isInheritorOrSelf(caller, owner, true)) {
        final String callerName = caller.getQualifiedName();
        final String ownerName = owner.getQualifiedName();
        if (callerName != null && ownerName != null) {
          holder.registerProblem(callerClassExpression,
                                 JavaBundle.message("inspection.handle.signature.not.subclass", callerName, ownerName));
        }
      }
    }
  }

  private static void checkArrayElementVarHandle(PsiMethodCallExpression factoryCallExpression, ProblemsHolder holder) {
    final PsiExpressionList argumentList = factoryCallExpression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length != 1) {
      holder.registerProblem(argumentList, JavaBundle.message("inspection.reflection.invocation.argument.count", 1));
      return;
    }
    final ReflectiveType argumentType = getReflectiveType(arguments[0]);
    if (argumentType == null || argumentType.getType() instanceof PsiArrayType) {
      return;
    }
    if (!argumentType.isPrimitive() && !argumentType.isExact()) {
      final String name = argumentType.getQualifiedName();
      if (JAVA_LANG_OBJECT.equals(name) || "java.io.Serializable".equals(name) || "java.lang.Cloneable".equals(name)) {
        return;
      }
    }
    holder.registerProblem(arguments[0], JavaBundle.message("inspection.reflect.handle.invocation.argument.not.array"));
  }

  private static @NotNull String getMethodDeclarationText(@NotNull String methodName, @NotNull ReflectiveSignature methodSignature) {
    final String returnType = methodSignature.getShortReturnType();
    return returnType + " " + methodName + methodSignature.getShortArgumentTypes();
  }

  private static @Nullable String getConstructorDeclarationText(@NotNull ReflectiveClass ownerClass, @NotNull ReflectiveSignature methodSignature) {
    final String className = ownerClass.getPsiClass().getName();
    if (className != null) {
      return getConstructorDeclarationText(className, methodSignature);
    }
    return null;
  }

  private static @NotNull String getConstructorDeclarationText(@NotNull String className, @NotNull ReflectiveSignature methodSignature) {
    // Return type of the constructor should be 'void'. If it isn't so let's make that mistake more noticeable.
    final String returnType = methodSignature.getShortReturnType();
    final String fakeReturnType = !JavaKeywords.VOID.equals(returnType) ? returnType + " " : "";
    return fakeReturnType + className + methodSignature.getShortArgumentTypes();
  }

  private static @NotNull @Unmodifiable List<PsiMethod> findMethodBySignature(@NotNull List<? extends PsiMethod> methods,
                                                                              @NotNull ReflectiveSignature expectedMethodSignature) {
    return ContainerUtil.filter(methods, method -> expectedMethodSignature.equals(getMethodSignature(method)));
  }

  private static class FieldTypeQuickFix extends PsiUpdateModCommandQuickFix {
    private final String myFieldTypeText;

    FieldTypeQuickFix(String fieldTypeText) {myFieldTypeText = fieldTypeText;}

    @Override
    public @Nls @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.handle.signature.change.type.fix.name", myFieldTypeText);
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiExpression typeExpression = factory.createExpressionFromText(myFieldTypeText + ".class", element);
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
      styleManager.shortenClassReferences(element.replace(typeExpression));
    }
  }

  private static class SwitchStaticnessQuickFix extends PsiUpdateModCommandQuickFix {
    private static final Map<String, String> STATIC_TO_NON_STATIC = Map.of(
      FIND_STATIC_GETTER, FIND_GETTER,
      FIND_STATIC_SETTER, FIND_SETTER,
      FIND_STATIC_VAR_HANDLE, FIND_VAR_HANDLE,
      FIND_STATIC, FIND_VIRTUAL);
    private static final Map<String, String> NON_STATIC_TO_STATIC = Map.of(
      FIND_GETTER, FIND_STATIC_GETTER,
      FIND_SETTER, FIND_STATIC_SETTER,
      FIND_VAR_HANDLE, FIND_STATIC_VAR_HANDLE,
      FIND_VIRTUAL, FIND_STATIC);

    private final String myReplacementName;

    SwitchStaticnessQuickFix(@NotNull String replacementName) {
      myReplacementName = replacementName;
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myReplacementName);
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiIdentifier identifier = factory.createIdentifier(myReplacementName);
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
      styleManager.shortenClassReferences(element.replace(identifier));
    }

    public static @Nullable LocalQuickFix createFix(@NotNull String methodName, boolean wasStatic) {
      final String replacementName = wasStatic ? STATIC_TO_NON_STATIC.get(methodName) : NON_STATIC_TO_STATIC.get(methodName);
      return replacementName != null ? new SwitchStaticnessQuickFix(replacementName) : null;
    }
  }

  private static class ReplaceSignatureQuickFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private final String myName;
    private final List<ReflectiveSignature> mySignatures;
    private final boolean myIsConstructor;

    ReplaceSignatureQuickFix(@Nullable PsiElement element,
                                    @NotNull String name,
                                    @NotNull List<ReflectiveSignature> signatures,
                                    boolean isConstructor) {
      super(element);
      myName = name;
      mySignatures = signatures;
      myIsConstructor = isConstructor;
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return getText();
    }

    @Override
    public @NotNull String getText() {
      if (mySignatures.size() == 1) {
        final String declarationText = getDeclarationText(mySignatures.get(0));
        return JavaBundle.message(myIsConstructor
                                         ? "inspection.handle.signature.use.constructor.fix.name"
                                         : "inspection.handle.signature.use.method.fix.name",
                                         declarationText);
      }
      return JavaBundle.message(myIsConstructor
                                       ? "inspection.handle.signature.use.constructor.fix.family.name"
                                       : "inspection.handle.signature.use.method.fix.family.name");
    }

    @Override
    public boolean startInWriteAction() {
      return mySignatures.size() == 1 || ApplicationManager.getApplication().isUnitTestMode();
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile file,
                       @Nullable Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        final PsiElement element = myStartElement.getElement();
        if (editor != null && element != null) {
          final ReflectiveSignature signature = editor.getUserData(DEFAULT_SIGNATURE);
          if (signature != null && mySignatures.contains(signature)) {
            applyFix(project, element, signature);
          }
          editor.putUserData(POSSIBLE_SIGNATURES, createLookupElements());
        }
        return;
      }

      if (mySignatures.size() == 1) {
        applyFix(project, startElement, mySignatures.get(0));
      }
      else if (editor != null) {
        showLookup(project, editor);
      }
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
      if (mySignatures.isEmpty()) return IntentionPreviewInfo.EMPTY;
      // Show first even if lookup is displayed
      ReflectiveSignature signature = mySignatures.get(0);
      PsiElement element = PsiTreeUtil.findSameElementInCopy(getStartElement(), file);
      if (element == null) return IntentionPreviewInfo.EMPTY;
      applyFix(project, element, signature);
      return IntentionPreviewInfo.DIFF;
    }

    private @NotNull List<LookupElement> createLookupElements() {
      return mySignatures.stream()
        .sorted(ReflectiveSignature::compareTo)
        .map(signature -> LookupElementBuilder.create(signature, "")
          .withIcon(signature.getIcon())
          .withPresentableText(myName + signature.getShortArgumentTypes())
          .withTypeText(!myIsConstructor ? signature.getShortReturnType() : null))
        .collect(Collectors.toList());
    }

    private void showLookup(@NotNull Project project, @NotNull Editor editor) {

      // Unfortunately, LookupManager.showLookup() doesn't invoke InsertHandler. A workaround with LookupListener is used.
      // To let the workaround work we need to make sure that noting is actually replaced by the default behavior of showLookup().
      editor.getSelectionModel().removeSelection();

      final List<LookupElement> items = createLookupElements();
      final LookupManager lookupManager = LookupManager.getInstance(project);
      final LookupEx lookup = lookupManager.showLookup(editor, items.toArray(LookupElement.EMPTY_ARRAY));
      if (lookup != null) {
        lookup.addLookupListener(new LookupListener() {
          @Override
          public void itemSelected(@NotNull LookupEvent event) {
            final LookupElement item = event.getItem();
            if (item != null) {
              final PsiElement element = getStartElement();
              final Object object = item.getObject();
              if (element != null && object instanceof ReflectiveSignature) {
                WriteAction.run(() -> applyFix(project, element, (ReflectiveSignature)object));
              }
            }
          }
        });
      }
    }

    private static void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ReflectiveSignature signature) {
      final String replacementText = getMethodTypeExpressionText(signature);
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiExpression replacement = factory.createExpressionFromText(replacementText, element);
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
      styleManager.shortenClassReferences(element.replace(replacement));
    }

    private @NotNull String getDeclarationText(@NotNull ReflectiveSignature signature) {
      return myIsConstructor ? getConstructorDeclarationText(myName, signature) : getMethodDeclarationText(myName, signature);
    }

    private static @Nullable LocalQuickFix createFix(@Nullable PsiElement element,
                                                     @NotNull String methodName,
                                                     @NotNull List<ReflectiveSignature> methodSignatures,
                                                     boolean isConstructor, boolean isOnTheFly) {
      if (isOnTheFly && !methodSignatures.isEmpty() || methodSignatures.size() == 1) {
        return new ReplaceSignatureQuickFix(element, methodName, methodSignatures, isConstructor);
      }
      return null;
    }
  }
}
