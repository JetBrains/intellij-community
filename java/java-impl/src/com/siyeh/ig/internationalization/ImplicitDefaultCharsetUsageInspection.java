// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.internationalization;

import com.intellij.codeInsight.intention.FileModifier.SafeTypeForPreview;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.module.JdkApiCompatibilityService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author Bas Leijdekkers
 */
public final class ImplicitDefaultCharsetUsageInspection extends BaseInspection implements CleanupLocalInspectionTool {

  private static final List<String> UTF_8_ARG = Collections.singletonList("java.nio.charset.StandardCharsets.UTF_8");
  private static final List<String> FALSE_AND_UTF_8_ARG = Arrays.asList("false", "java.nio.charset.StandardCharsets.UTF_8");

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    if (infos[0] instanceof PsiNewExpression) {
      return InspectionGadgetsBundle.message("implicit.default.charset.usage.constructor.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("implicit.default.charset.usage.problem.descriptor");
    }
  }

  @SafeTypeForPreview
  private static final class CharsetOverload {
    static final CharsetOverload NONE = new CharsetOverload(null, Collections.emptyList());

    final PsiMethod myMethod;
    final List<String> myAdditionalArguments;

    private CharsetOverload(PsiMethod method, List<String> arguments) {
      myMethod = method;
      myAdditionalArguments = arguments;
    }

    LocalQuickFix createFix(LanguageLevel level) {
      return myMethod == null || JdkApiCompatibilityService.getInstance().firstCompatibleLanguageLevel(myMethod, level) != null
             ? null
             : new AddUtf8CharsetFix(this);
    }

    Stream<String> additionalArguments() {
      return myAdditionalArguments.stream();
    }
  }

  private static final Key<CharsetOverload> HAS_CHARSET_OVERLOAD = Key.create("Method has Charset overload");

  private static @NotNull CharsetOverload getCharsetOverload(PsiMethod method) {
    if (method == null) return CharsetOverload.NONE;

    CharsetOverload charsetOverload = method.getUserData(HAS_CHARSET_OVERLOAD);
    if (charsetOverload == null) {
      PsiMethod methodWithCharsetArgument = null;
      PsiClass aClass = method.getContainingClass();
      List<String> args = UTF_8_ARG;
      if (aClass != null) {
        MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
        PsiType charsetType =
          JavaPsiFacade.getElementFactory(method.getProject()).createTypeByFQClassName("java.nio.charset.Charset", method.getResolveScope());
        PsiType[] parameterTypes = signature.getParameterTypes();
        if (method.isConstructor() && "java.io.PrintWriter".equals(aClass.getQualifiedName()) && parameterTypes.length == 1 &&
            parameterTypes[0].equalsToText("java.io.OutputStream")) {
          parameterTypes = ArrayUtil.append(parameterTypes, PsiTypes.booleanType());
          args = FALSE_AND_UTF_8_ARG;
        }
        MethodSignature newSignature = MethodSignatureUtil
          .createMethodSignature(signature.getName(), ArrayUtil.append(parameterTypes, charsetType),
                                 signature.getTypeParameters(), signature.getSubstitutor(), signature.isConstructor()
          );
        methodWithCharsetArgument = MethodSignatureUtil.findMethodBySignature(aClass, newSignature, false);
      }
      charsetOverload = methodWithCharsetArgument != null ? new CharsetOverload(methodWithCharsetArgument, args) : CharsetOverload.NONE;
      method.putUserData(HAS_CHARSET_OVERLOAD, charsetOverload);
    }
    return charsetOverload;
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    PsiCallExpression call = (PsiCallExpression)infos[0];
    LanguageLevel level = PsiUtil.getLanguageLevel(call);
    if (!level.isAtLeast(LanguageLevel.JDK_1_7)) return null;
    PsiMethod method = call.resolveMethod();
    return getCharsetOverload(method).createFix(level);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ImplicitDefaultCharsetUsageVisitor();
  }

  private static class ImplicitDefaultCharsetUsageVisitor extends BaseInspectionVisitor {
    private static final CallMatcher METHODS = CallMatcher.anyOf(
      CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_STRING, "getBytes").parameterCount(0),
      CallMatcher.staticCall("org.apache.commons.io.IOUtils", "toByteArray").parameterTypes(CommonClassNames.JAVA_LANG_STRING),
      CallMatcher.staticCall("org.apache.commons.io.IOUtils", "toByteArray").parameterTypes("java.io.Reader"),
      CallMatcher.staticCall("org.apache.commons.io.IOUtils", "toCharArray", "toString", "readLines").parameterTypes("java.io.InputStream"),
      CallMatcher.staticCall("org.apache.commons.io.IOUtils", "toString").parameterTypes("java.net.URI"),
      CallMatcher.staticCall("org.apache.commons.io.IOUtils", "toString").parameterTypes("java.net.URL"),
      CallMatcher.staticCall("org.apache.commons.io.IOUtils", "toInputStream").parameterTypes(CommonClassNames.JAVA_LANG_CHAR_SEQUENCE),
      CallMatcher.staticCall("org.apache.commons.io.IOUtils", "toInputStream").parameterTypes(CommonClassNames.JAVA_LANG_STRING)
    );

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (METHODS.test(expression)) {
        registerMethodCallError(expression, expression);
      }
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiMethod constructor = expression.resolveConstructor();
      if (constructor == null) {
        return;
      }
      final PsiClass aClass = constructor.getContainingClass();
      if (aClass == null) {
        return;
      }
      final PsiParameterList parameterList = constructor.getParameterList();
      final int count = parameterList.getParametersCount();
      if (count == 0) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final String qName = aClass.getQualifiedName();
      if (CommonClassNames.JAVA_LANG_STRING.equals(qName)) {
        if (!parameters[0].getType().equalsToText("byte[]") || hasCharsetType(parameters[count - 1])) {
          return;
        }
      }
      else if ("java.io.InputStreamReader".equals(qName) ||
               "java.io.OutputStreamWriter".equals(qName) ||
               "java.io.PrintStream".equals(qName)) {
        if (hasCharsetType(parameters[count - 1])) {
          return;
        }
      }
      else if ("java.io.PrintWriter".equals(qName)) {
        if (count > 1 && hasCharsetType(parameters[count - 1]) || parameters[0].getType().equalsToText("java.io.Writer")) {
          return;
        }
        if (count == 1) {
          PsiExpressionList args = expression.getArgumentList();
          if (args != null &&
              PsiUtil.skipParenthesizedExprDown(ArrayUtil.getFirstElement(args.getExpressions())) instanceof PsiReferenceExpression ref &&
              ref.resolve() instanceof PsiField field) {
            if (field.getName().equals("out") || field.getName().equals("err")) {
              PsiClass containingClass = field.getContainingClass();
              if (containingClass != null && CommonClassNames.JAVA_LANG_SYSTEM.equals(containingClass.getQualifiedName())) {
                // new PrintWriter(System.out): likely system default encoding is expected
                return;
              }
            }
          }
        }
      }
      else if ("java.util.Formatter".equals(qName)) {
        if (count > 1 && hasCharsetType(parameters[1])) {
          return;
        }
        final PsiType firstType = parameters[0].getType();
        if (!firstType.equalsToText(CommonClassNames.JAVA_LANG_STRING) && !firstType.equalsToText("java.io.File") &&
          !firstType.equalsToText("java.io.OutputStream")) {
          return;
        }
      }
      else if ("java.util.Scanner".equals(qName)) {
        if (count > 1 && hasCharsetType(parameters[1])) {
          return;
        }
        final PsiType firstType = parameters[0].getType();
        if (!firstType.equalsToText("java.io.InputStream") && !firstType.equalsToText("java.io.File") &&
          !firstType.equalsToText("java.nio.file.Path") && !firstType.equalsToText("java.nio.channels.ReadableByteChannel")) {
          return;
        }
      }
      else if ("java.io.FileReader".equals(qName) || "java.io.FileWriter".equals(qName)) {
        if (count > 1 && hasCharsetType(parameters[1])) {
          return;
        }
      }
      else {
        return;
      }
      registerNewExpressionError(expression, expression);

    }

    private static boolean hasCharsetType(PsiVariable variable) {
      return TypeUtils.variableHasTypeOrSubtype(variable, CommonClassNames.JAVA_LANG_STRING,
                                                "java.nio.charset.Charset",
                                                "java.nio.charset.CharsetEncoder",
                                                "java.nio.charset.CharsetDecoder");
    }
  }

  private static final class AddUtf8CharsetFix extends PsiUpdateModCommandQuickFix {
    /**
     * Refers to the method but it is read-only
     */
    private final CharsetOverload myCharsetOverload;

    private AddUtf8CharsetFix(CharsetOverload charsetOverload) {
      myCharsetOverload = charsetOverload;
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      PsiCallExpression call = PsiTreeUtil.getParentOfType(startElement, PsiCallExpression.class);
      if (call == null) return;
      PsiExpressionList arguments = call.getArgumentList();
      if (arguments == null) return;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      myCharsetOverload.additionalArguments().map(arg -> factory.createExpressionFromText(arg, call)).forEach(arguments::add);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(arguments);
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("implicit.default.charset.usage.fix.family.name");
    }
  }
}
