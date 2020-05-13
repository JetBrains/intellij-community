// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteCatchFix;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteMultiCatchFix;
import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.intellij.psi.CommonClassNames.*;

public class CharsetObjectCanBeUsedInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  private static final CharsetCallMatcher[] MATCHERS = {
    new CharsetConstructorMatcher("java.io.InputStreamReader", "java.io.InputStream", ""),
    new CharsetConstructorMatcher("java.io.OutputStreamWriter", "java.io.OutputStream", ""),
    new CharsetConstructorMatcher(JAVA_LANG_STRING, "byte[]", "int", "int", ""),
    new CharsetConstructorMatcher(JAVA_LANG_STRING, "byte[]", ""),
    new CharsetMethodMatcher(JAVA_LANG_STRING, "getBytes", ""),

    // Java 10
    new CharsetConstructorMatcher("java.util.Scanner", "java.io.InputStream", ""),
    new CharsetConstructorMatcher("java.util.Scanner", JAVA_IO_FILE, ""),
    new CharsetConstructorMatcher("java.util.Scanner", "java.nio.file.Path", ""),
    new CharsetConstructorMatcher("java.util.Scanner", "java.nio.channels.ReadableByteChannel", ""),
    new CharsetConstructorMatcher("java.io.PrintStream", "java.io.OutputStream", "boolean", ""),
    new CharsetConstructorMatcher("java.io.PrintStream", JAVA_LANG_STRING, ""),
    new CharsetConstructorMatcher("java.io.PrintStream", JAVA_IO_FILE, ""),
    new CharsetConstructorMatcher("java.io.PrintWriter", "java.io.OutputStream", "boolean", ""),
    new CharsetConstructorMatcher("java.io.PrintWriter", JAVA_LANG_STRING, ""),
    new CharsetConstructorMatcher("java.io.PrintWriter", JAVA_IO_FILE, ""),
    new CharsetMethodMatcher("java.io.ByteArrayOutputStream", "toString", ""),
    new CharsetMethodMatcher("java.net.URLDecoder", "decode", JAVA_LANG_STRING, ""),
    new CharsetMethodMatcher("java.net.URLEncoder", "encode", JAVA_LANG_STRING, ""),
    new CharsetMethodMatcher("java.nio.channels.Channels", "newReader", "java.nio.channels.ReadableByteChannel", ""),
    new CharsetMethodMatcher("java.nio.channels.Channels", "newWriter", "java.nio.channels.WritableByteChannel", ""),
    new CharsetMethodMatcher(JAVA_UTIL_PROPERTIES, "storeToXML", "java.io.OutputStream", JAVA_LANG_STRING, ""),
    
    // Apache IO
    new CharsetMethodMatcher("org.apache.commons.io.IOUtils", "toString", "java.io.InputStream", ""),
    new CharsetMethodMatcher("org.apache.commons.io.IOUtils", "toString", "java.net.URI", ""),
    new CharsetMethodMatcher("org.apache.commons.io.IOUtils", "toString", "java.net.URL", ""),
  };

  private static final CallMatcher FOR_NAME_MATCHER =
    CallMatcher.staticCall("java.nio.charset.Charset", "forName").parameterTypes(JAVA_LANG_STRING);

  private static final Map<String, String> SUPPORTED_CHARSETS =
    ContainerUtil.<String, String>immutableMapBuilder()
      .put("US-ASCII", "US_ASCII")
      .put("ASCII", "US_ASCII")
      .put("ISO646-US", "US_ASCII")
      .put("ISO-8859-1", "ISO_8859_1")
      .put("UTF-8", "UTF_8")
      .put("UTF8", "UTF_8")
      .put("UTF-16BE", "UTF_16BE")
      .put("UTF16BE", "UTF_16BE")
      .put("UTF-16LE", "UTF_16LE")
      .put("UTF16LE", "UTF_16LE")
      .put("UTF-16", "UTF_16")
      .put("UTF16", "UTF_16")
      .build();

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    LanguageLevel languageLevel = PsiUtil.getLanguageLevel(holder.getFile());
    if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitCallExpression(PsiCallExpression call) {
        CharsetMatch match = StreamEx.of(MATCHERS)
          .map(matcher -> matcher.extractCharsetMatch(languageLevel, call))
          .nonNull()
          .findFirst().orElse(null);
        if (match == null) return;
        addCharsetReplacement(match.myStringCharset, match.myStringCharset);
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);
        if (!FOR_NAME_MATCHER.matches(call)) return;
        PsiExpressionList arguments = call.getArgumentList();
        PsiExpression charset = arguments.getExpressions()[0];
        addCharsetReplacement(call, charset);
      }

      private void addCharsetReplacement(@NotNull PsiElement place, @NotNull PsiExpression charset) {
        String charsetString = getCharsetString(charset);
        if (charsetString == null) return;
        String constantName = "StandardCharsets." + SUPPORTED_CHARSETS.get(charsetString);
        holder.registerProblem(place, JavaBundle.message("inspection.charset.object.can.be.used.message", constantName),
                               new CharsetObjectCanBeUsedFix(constantName));
      }

      @Nullable
      private String getCharsetString(PsiExpression charsetExpression) {
        charsetExpression = PsiUtil.skipParenthesizedExprDown(charsetExpression);
        String charsetString = ObjectUtils.tryCast(ExpressionUtils.computeConstantExpression(charsetExpression), String.class);
        if (charsetString == null) return null;
        charsetString = StringUtil.toUpperCase(charsetString);
        if (!SUPPORTED_CHARSETS.containsKey(charsetString)) return null;
        if (charsetExpression instanceof PsiLiteralExpression) return charsetString;
        if (charsetExpression instanceof PsiReferenceExpression) {
          String name = ((PsiReferenceExpression)charsetExpression).getReferenceName();
          if (name == null) return null;
          String baseName = StringUtil.toLowerCase(name.replaceAll("[^A-Z0-9]", ""));
          String baseCharset = StringUtil.toLowerCase(charsetString.replaceAll("[^A-Z0-9]", ""));
          // Do not report constants which name is not based on charset name (like "ENCODING", "DEFAULT_ENCODING", etc.)
          // because replacement might not be well-suitable
          if (!baseName.contains(baseCharset)) return null;
          return charsetString;
        }
        return null;
      }
    };
  }

  abstract static class CharsetCallMatcher {
    @NotNull final String myClassName;
    final String @NotNull [] myParameters;
    final int myCharsetParameterIndex;

    CharsetCallMatcher(@NotNull String className, String @NotNull ... parameters) {
      myClassName = className;
      myParameters = parameters;
      int index = -1;
      for (int i = 0; i < parameters.length; i++) {
        if (parameters[i].isEmpty()) {
          if (index == -1) {
            index = i;
          }
          else {
            throw new IllegalArgumentException("Empty parameter type must be specified exactly once");
          }
        }
      }
      if (index == -1) {
        throw new IllegalArgumentException("No empty parameter type is specified");
      }
      myCharsetParameterIndex = index;
    }

    @Contract("null,_ -> false")
    final boolean checkMethod(PsiMethod method, @NotNull String charsetType) {
      if (method == null) return false;
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || !myClassName.equals(containingClass.getQualifiedName())) return false;
      PsiParameterList list = method.getParameterList();
      if (list.getParametersCount() != myParameters.length) return false;
      PsiParameter[] parameters = list.getParameters();
      for (int i = 0; i < myParameters.length; i++) {
        PsiType parameterType = parameters[i].getType();
        if (!parameterType.equalsToText(myParameters[i].isEmpty() ? charsetType : myParameters[i])) return false;
      }
      return true;
    }

    @Nullable
    final CharsetMatch createMatch(LanguageLevel languageLevel, PsiMethod method, PsiExpressionList arguments) {
      PsiExpression argument = arguments.getExpressions()[myCharsetParameterIndex];
      PsiClass aClass = method.getContainingClass();
      if (aClass == null) return null;

      PsiMethod[] candidates = method.isConstructor() ? aClass.getConstructors() : aClass.findMethodsByName(method.getName(), false);
      PsiMethod charsetMethod = Arrays.stream(candidates)
        .filter(psiMethod -> checkMethod(psiMethod, "java.nio.charset.Charset"))
        .filter(psiMethod -> Java15APIUsageInspection.getLastIncompatibleLanguageLevel(psiMethod, languageLevel) == null)
        .findFirst().orElse(null);
      if (charsetMethod == null) return null;
      return new CharsetMatch(argument, method, charsetMethod);
    }

    @Nullable
    abstract CharsetMatch extractCharsetMatch(LanguageLevel languageLevel, PsiCallExpression call);
  }

  static class CharsetConstructorMatcher extends CharsetCallMatcher {
    CharsetConstructorMatcher(@NotNull String className, String @NotNull ... parameters) {
      super(className, parameters);
    }

    @Override
    CharsetMatch extractCharsetMatch(LanguageLevel languageLevel, PsiCallExpression call) {
      if (!(call instanceof PsiNewExpression)) return null;
      PsiNewExpression newExpression = (PsiNewExpression)call;
      PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null || argumentList.getExpressionCount() != myParameters.length) return null;
      PsiMethod method = call.resolveMethod();
      if (!checkMethod(method, JAVA_LANG_STRING) || !method.isConstructor()) return null;
      return createMatch(languageLevel, method, argumentList);
    }
  }

  static class CharsetMethodMatcher extends CharsetCallMatcher {
    @NotNull private final String myMethodName;

    CharsetMethodMatcher(@NotNull String className, @NotNull String methodName, String @NotNull ... parameters) {
      super(className, parameters);
      myMethodName = methodName;
    }

    @Override
    CharsetMatch extractCharsetMatch(LanguageLevel languageLevel, PsiCallExpression call) {
      if (!(call instanceof PsiMethodCallExpression)) return null;
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)call;
      if (!myMethodName.equals(methodCallExpression.getMethodExpression().getReferenceName())) return null;
      PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      if (argumentList.getExpressionCount() != myParameters.length) return null;
      PsiMethod method = call.resolveMethod();
      if (!checkMethod(method, JAVA_LANG_STRING)) return null;
      return createMatch(languageLevel, method, argumentList);
    }
  }

  static class CharsetMatch {
    @NotNull final PsiExpression myStringCharset;
    @NotNull final PsiMethod myStringMethod;
    @NotNull final PsiMethod myCharsetMethod;

    CharsetMatch(@NotNull PsiExpression charset, @NotNull PsiMethod stringMethod, @NotNull PsiMethod charsetMethod) {
      myStringCharset = charset;
      myStringMethod = stringMethod;
      myCharsetMethod = charsetMethod;
    }
  }

  static class CharsetObjectCanBeUsedFix implements LocalQuickFix {
    private final String myConstantName;

    CharsetObjectCanBeUsedFix(String constantName) {
      myConstantName = constantName;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("inspection.charset.object.can.be.used.fix.name", myConstantName);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.charset.object.can.be.used.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiExpression expression = ObjectUtils.tryCast(descriptor.getStartElement(), PsiExpression.class);
      if (expression == null) return;
      PsiElement anchor = FOR_NAME_MATCHER.matches(expression) ? null : PsiTreeUtil.getParentOfType(expression, PsiCallExpression.class);
      CommentTracker ct = new CommentTracker();
      String replacement = "java.nio.charset." + myConstantName;
      PsiReferenceExpression ref = (PsiReferenceExpression)ct.replaceAndRestoreComments(expression, replacement);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(ref);
      if (anchor == null) return;
      while (true) {
        PsiTryStatement tryStatement =
          PsiTreeUtil.getParentOfType(anchor, PsiTryStatement.class, true, PsiMember.class, PsiLambdaExpression.class);
        if (tryStatement == null) break;
        PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        if (PsiTreeUtil.isAncestor(tryBlock, anchor, true)) {
          for (PsiParameter parameter : tryStatement.getCatchBlockParameters()) {
            List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
            for (PsiTypeElement element : typeElements) {
              PsiType type = element.getType();
              if (type.equalsToText("java.io.UnsupportedEncodingException") ||
                  type.equalsToText("java.io.IOException")) {
                Collection<PsiClassType> unhandledExceptions = ExceptionUtil.collectUnhandledExceptions(tryBlock, tryBlock);
                PsiResourceList resourceList = tryStatement.getResourceList();
                if (resourceList != null) {
                  Collection<PsiClassType> resourceExceptions = ExceptionUtil.collectUnhandledExceptions(resourceList, resourceList);
                  unhandledExceptions = StreamEx.of(unhandledExceptions, resourceExceptions).toFlatList(Function.identity());
                }
                if(unhandledExceptions.stream().noneMatch(ue -> ue.isAssignableFrom(type) || type.isAssignableFrom(ue))) {
                  if(parameter.getType() instanceof PsiDisjunctionType) {
                    DeleteMultiCatchFix.deleteCaughtExceptionType(element);
                  } else {
                    DeleteCatchFix.deleteCatch(parameter);
                  }
                }
                return;
              }
              if (type.equalsToText(JAVA_LANG_EXCEPTION) || type.equalsToText(JAVA_LANG_THROWABLE)) {
                return;
              }
            }
          }
        }
        anchor = tryStatement;
      }
    }
  }
}
