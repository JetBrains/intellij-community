// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MismatchedJavadocCodeInspection extends AbstractBaseJavaLocalInspectionTool {

  public static final Pattern
    RETURN_LINE_START = Pattern.compile("@return\\s+(\\S+) (iff?|when|in case of|in the case of) ");
  public static final Pattern
    RETURN_LINE_START_TYPE = Pattern.compile("@return\\s+(?:the )?(\\S+) (that |describing |containing |of |with |whose |which )");
  public static final Pattern
    RETURN_LINE_START_TYPE_SINGULAR = Pattern.compile("@return\\s+an? (\\S+) (that |describing |containing |of |with |whose |which )");
  public static final Pattern
    RETURN_LINE_MIDDLE = Pattern.compile("(?:\n\\s*\\*|;|,)\\s+(\\S+) (iff?\\s|when\\s|otherwise|in case of|in the case of)",
                                         Pattern.MULTILINE);
  public static final Pattern DESCRIPTION =
    Pattern.compile("^\\s+Returns (\\S+) (iff?\\s|when\\s|in case of|in the case of)", Pattern.CASE_INSENSITIVE);

  private static final Map<String, Predicate<PsiType>> TYPE_SIMPLE_NAMES =
    Map.of("map", t -> InheritanceUtil.isInheritor(t, CommonClassNames.JAVA_UTIL_MAP),
           "set", t -> InheritanceUtil.isInheritor(t, CommonClassNames.JAVA_UTIL_SET),
           "list", t -> InheritanceUtil.isInheritor(t, CommonClassNames.JAVA_UTIL_LIST),
           "number", t -> TypeConversionUtil.isNumericType(t),
           "boolean", t -> TypeConversionUtil.isBooleanType(t),
           "array", t -> t instanceof PsiArrayType,
           "string", t -> false);

  enum Kind {
    LIKELY_VALUE,
    MAYBE_VALUE,
    MAYBE_TYPE,
    MAYBE_TYPE_SINGULAR
  }

  record ReturnItem(@NotNull Kind kind, @NotNull TextRange range) {
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(PsiMethod method) {
        PsiDocComment comment = method.getDocComment();
        if (comment == null) {
          return;
        }
        List<ReturnItem> items = extractValues(comment);
        String commentText = comment.getText();
        for (ReturnItem item : items) {
          String valueText = item.range.substring(commentText);
          String incompatibleMessage = getIncompatibleMessage(valueText, item.kind, method);
          if (incompatibleMessage != null) {
            holder.registerProblem(comment, item.range, incompatibleMessage);
          }
        }
      }

      private static @Nls @Nullable String getIncompatibleMessage(@NotNull String text, @NotNull Kind kind, @NotNull PsiMethod method) {
        PsiType returnType = method.getReturnType();
        if (returnType == null || PsiTypes.voidType().equals(returnType)) return null;
        PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(returnType);
        if (kind == Kind.MAYBE_TYPE || kind == Kind.MAYBE_TYPE_SINGULAR) {
          String typeName = text.toLowerCase(Locale.ROOT);
          String normalized = typeName.equals("count") || typeName.equals("amount") ? "number" : typeName;
          if (TYPE_SIMPLE_NAMES.containsKey(normalized)) {
            for (Map.Entry<String, Predicate<PsiType>> entry : TYPE_SIMPLE_NAMES.entrySet()) {
              if (!normalized.equals(entry.getKey()) && entry.getValue().test(returnType)) {
                return InspectionsBundle.message("inspection.mismatch.javadoc.reason.different.type", typeName, entry.getKey());
              }
            }
          }
          if (kind == Kind.MAYBE_TYPE_SINGULAR) {
            PsiType type = PsiUtil.substituteTypeParameter(returnType, CommonClassNames.JAVA_UTIL_COLLECTION, 0, false);
            if (type instanceof PsiClassType && type.getPresentableText().equalsIgnoreCase(typeName) &&
                !((PsiClassType)type).rawType().isAssignableFrom(returnType)) {
              return InspectionsBundle.message("inspection.mismatch.javadoc.reason.single.collection", type.getPresentableText());
            }
          }
          return null;
        }
        if (text.equals("null")) {
          if (returnType instanceof PsiPrimitiveType) {
            return InspectionsBundle.message("inspection.mismatch.javadoc.reason.null.primitive");
          }
          NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(method.getProject()).findEffectiveNullabilityInfo(method);
          if (info != null && info.getNullability() == Nullability.NOT_NULL) {
            PsiClass psiClass = info.getAnnotation().resolveAnnotationType();
            if (psiClass != null) {
              return InspectionsBundle.message("inspection.mismatch.javadoc.reason.null.annotated", psiClass.getName());
            }
          }
        }
        if (StringUtil.isJavaIdentifier(text) &&
            (text.equals("true") || text.equals("false") || text.equals(text.toUpperCase(Locale.ROOT)))) {
          if (aClass != null && aClass.isEnum()) {
            if (StreamEx.of(aClass.getFields()).select(PsiEnumConstant.class)
              .map(PsiEnumConstant::getName).noneMatch(name -> name.equalsIgnoreCase(text))) {
              return InspectionsBundle.message("inspection.mismatch.javadoc.reason.enum", text, aClass.getName());
            }
          }
        }
        if (text.equals("true") || text.equals("false")) {
          if (!TypeConversionUtil.isBooleanType(returnType)) {
            if (kind == Kind.MAYBE_VALUE) {
              if (InheritanceUtil.isInheritor(returnType, CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE) ||
                  returnType instanceof PsiClassType && ContainerUtil.exists(((PsiClassType)returnType).getParameters(),
                                                                             TypeConversionUtil::isBooleanType)) {
                return null;
              }
            }
            return InspectionsBundle.message("inspection.mismatch.javadoc.reason.boolean", text);
          }
        }
        return null;
      }

      private List<ReturnItem> extractValues(PsiDocComment comment) {
        List<ReturnItem> ranges = new ArrayList<>();
        for (PsiElement child : comment.getChildren()) {
          if (child instanceof PsiDocTag && ((PsiDocTag)child).getName().equals("return")) {
            processLine(RETURN_LINE_START, child, Kind.LIKELY_VALUE, ranges);
            processLine(RETURN_LINE_MIDDLE, child, Kind.MAYBE_VALUE, ranges);
            processLine(RETURN_LINE_START_TYPE, child, Kind.MAYBE_TYPE, ranges);
            processLine(RETURN_LINE_START_TYPE_SINGULAR, child, Kind.MAYBE_TYPE_SINGULAR, ranges);
          }
          else if (child instanceof PsiDocToken && ((PsiDocToken)child).getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA) {
            processLine(DESCRIPTION, child, Kind.LIKELY_VALUE, ranges);
          }
        }
        return ranges;
      }

      private static void processLine(Pattern pattern, PsiElement child, Kind kind, List<ReturnItem> ranges) {
        String text = child.getText();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
          int start = matcher.start(1);
          int end = matcher.end(1);
          ranges.add(new ReturnItem(kind, TextRange.create(start, end).shiftRight(child.getStartOffsetInParent())));
        }
      }
    };
  }
}
