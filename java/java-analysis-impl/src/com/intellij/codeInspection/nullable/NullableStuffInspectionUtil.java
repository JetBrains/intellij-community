// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.nullable;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiCapturedWildcardType;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.JavaTypeNullabilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class NullableStuffInspectionUtil {
  static @NotNull @NlsSafe HtmlChunk getNullabilityConflictPresentation(@NotNull JavaTypeNullabilityUtil.NullabilityConflictContext context) {
    HtmlChunk expectedChunk = getSideChunk(context, JavaTypeNullabilityUtil.Side.EXPECTED);
    HtmlChunk actualChunk = getSideChunk(context, JavaTypeNullabilityUtil.Side.ACTUAL);
    if (expectedChunk.isEmpty() || actualChunk.isEmpty()) return HtmlChunk.empty();
    return HtmlChunk.tag("table")
      .children(expectedChunk, actualChunk);
  }

  private static @NotNull HtmlChunk getSideChunk(@NotNull JavaTypeNullabilityUtil.NullabilityConflictContext context,
                                                  @NotNull JavaTypeNullabilityUtil.Side side) {
    PsiElement place = context.getPlace(side);
    if (place == null) return HtmlChunk.empty();
    int dimensionsInArray = (context.getType(side) instanceof PsiArrayType type) ? type.getArrayDimensions() : 0;

    PsiTypeElement topmostType = PsiTreeUtil.getTopmostParentOfType(place, PsiTypeElement.class);
    if (topmostType == null) return HtmlChunk.empty();

    PsiTypeElement target = PsiTreeUtil.getParentOfType(place, PsiTypeElement.class, false);
    if (target == null) return HtmlChunk.empty();

    PsiType outerType = topmostType.getType();
    if (target == topmostType) {
      // Presentation is not handling with the whole type
      return HtmlChunk.empty();
    }

    List<Integer> path = computeTypeArgumentPath(topmostType, target, dimensionsInArray);
    if (path == null) return HtmlChunk.empty();

    Context presentationContext = getPresentationContext(outerType, path, outerType instanceof PsiArrayType);

    String typeText = presentationContext.sb.toString();
    String annotationText = getAnnotationText(context, side);
    if (presentationContext.position == null || annotationText == null) return HtmlChunk.empty();
    HtmlChunk result = generateHtmlChunk(typeText, "@" + annotationText, side, presentationContext.position);
    return result;
  }

  private static @Nullable String getAnnotationText(@NotNull JavaTypeNullabilityUtil.NullabilityConflictContext context, @NotNull JavaTypeNullabilityUtil.Side side) {
    PsiAnnotation annotation = context.getAnnotation(side);
    if (annotation == null) return null;
    PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
    if (ref == null) return null;
    return ref.getReferenceName();
  }

  private static @NotNull HtmlChunk generateHtmlChunk(@NlsSafe String text,
                                                      @NlsSafe String annotationText,
                                                      @NotNull JavaTypeNullabilityUtil.Side side,
                                                      int position) {
    return HtmlChunk.tag("tr")
      .children(
        HtmlChunk.tag("td")
          .addText(
            JavaAnalysisBundle.message(
              side == JavaTypeNullabilityUtil.Side.EXPECTED ? "expected.type.nullability.conflict.message"
                                                            : "actual.type.nullability.conflict.message"
            )
          ),
        HtmlChunk.tag("td").children(
          HtmlChunk.text(text.substring(0, position)),
          HtmlChunk.tag("b").addText(annotationText),
          HtmlChunk.text(" "),
          HtmlChunk.text(text.substring(position))
        )
      );
  }

  private static @Nullable List<@NotNull Integer> computeTypeArgumentPath(@NotNull PsiTypeElement top, @NotNull PsiTypeElement target, int firstArrayDepth) {
    List<Integer> indices = new ArrayList<>();
    PsiTypeElement current = target;
    boolean isFirstArrayOccurence = true;
    while (true) {
      PsiTypeElement parentTypeElement = PsiTreeUtil.getParentOfType(current, PsiTypeElement.class, true);
      if (parentTypeElement == null) return null;
      PsiType parentType = parentTypeElement.getType();
      if (parentType instanceof PsiClassType) {
        PsiJavaCodeReferenceElement ref = parentTypeElement.getInnermostComponentReferenceElement();
        if (ref == null) return null;
        PsiReferenceParameterList params = ref.getParameterList();
        if (params == null) return null;
        PsiTypeElement[] args = params.getTypeParameterElements();
        int found = ArrayUtil.indexOf(args, current);
        if (found < 0) return null;
        indices.add(found);
      }
      else if (parentType instanceof PsiWildcardType ||
               parentType instanceof PsiCapturedWildcardType) {
        indices.add(0);
      } else if (parentType instanceof PsiArrayType arrayType) {
        int candidateArrayDepth = arrayType.getArrayDimensions();
        int numberToAdd;
        if (isFirstArrayOccurence && candidateArrayDepth == firstArrayDepth) {
          numberToAdd = 0;
        } else if (isFirstArrayOccurence && firstArrayDepth > 0) {
          numberToAdd = firstArrayDepth;
        } else {
          numberToAdd = candidateArrayDepth;
        }
        for (int i = 0; i < numberToAdd; i++) {
          indices.add(0);
        }
      }
      if (parentTypeElement == top) break;
      current = parentTypeElement;
      isFirstArrayOccurence = false;
    }
    Collections.reverse(indices);
    return indices;
  }

  private static @NotNull Context getPresentationContext(@NotNull PsiType type, List<Integer> path, boolean isInsideArray) {
    Context context = new Context();
    buildTextRepresentation(type, context, path, 0, isInsideArray);
    return context;
  }

  /**
   * Constructs the simplified presentable view of the type and detects the place in which the nullability annotation should be inserted.
   * The result of this method is stored in the {@link Context#sb} member.
   * @param isInsideArray corresponds to the state whether the method is called within the array dimension. It is used to distinguish
   *                      cases {@code @Nullable String[]} and {@code String @Nullable [][]}.
   */
  private static void buildTextRepresentation(@NotNull PsiType type,
                                              @NotNull Context context,
                                              @NotNull List<@NotNull Integer> path,
                                              int depth,
                                              boolean isInsideArray) {
    if (depth == path.size()) {
      if (type instanceof PsiArrayType arrayType && isInsideArray) {
        context.sb.append(arrayType.getPresentableText(false));
        context.sb.append(" ");
        context.position = context.sb.length();
      }
      else if (type instanceof PsiArrayType arrayType) {
        context.sb.append(arrayType.getDeepComponentType().getPresentableText(false));
        context.sb.append(" ");
        context.position = context.sb.length();
        context.sb.repeat("[]", Math.max(0, arrayType.getArrayDimensions()));
      }
      else {
        String text = type.getPresentableText(false);
        context.position = context.sb.length();
        context.sb.append(text);
      }
      return;
    }
    if (type instanceof PsiArrayType arrayType) {
      PsiType component = arrayType.getComponentType();
      buildTextRepresentation(component, context, path, depth + 1, true);
      context.sb.append("[]");
      return;
    }
    if (type instanceof PsiWildcardType wc) {
      String prefix = wc.isExtends() ? "? extends " : wc.isSuper() ? "? super " : "?";
      context.sb.append(prefix);
      PsiType bound = wc.getBound();
      if (bound != null) {
        buildTextRepresentation(bound, context, path, depth + 1, false);
      }
      return;
    }
    if (type instanceof PsiClassType cls) {
      String name = cls.getClassName();
      if (name == null) name = cls.getPresentableText(false);
      context.sb.append(name);
      PsiType[] params = cls.getParameters();
      if (params.length > 0) {
        context.sb.append('<');
        for (int i = 0; i < params.length; i++) {
          if (i > 0) context.sb.append(", ");
          if (i == path.get(depth)) {
            buildTextRepresentation(params[i], context, path, depth + 1, false);
          }
          else {
            context.sb.append(params[i].getPresentableText(false));
          }
        }
        context.sb.append('>');
      }
      return;
    }

    context.sb.append(type.getPresentableText(false));
  }

  private static final class Context {
    private @Nullable Integer position = null;
    private final @NotNull StringBuilder sb = new StringBuilder();
  }
}
