// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.ProjectIconsAccessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.evaluation.UEvaluationContextKt;
import org.jetbrains.uast.expressions.UInjectionHost;
import org.jetbrains.uast.values.UConstant;
import org.jetbrains.uast.values.UStringConstant;
import org.jetbrains.uast.values.UValue;
import org.jetbrains.uast.values.UValueKt;

import javax.swing.*;
import java.util.*;

/**
 * Shows small (16x16 or less) icons as gutters.
 * <p/>
 * Works in places where it's possible to resolve from literal expression
 * to an icon image.
 *
 * @author Konstantin Bulenkov
 */
final class IconLineMarkerProvider extends LineMarkerProviderDescriptor {
  @Override
  public @NotNull String getName() {
    return JavaBundle.message("icon.preview");
  }

  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
    return null;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements, @NotNull Collection<? super LineMarkerInfo<?>> result) {
    Set<PsiClassType> uniqueTypes = new HashSet<>();
    Set<PsiClassType> applicableTypes = new HashSet<>();

    for (PsiElement element : elements) {
      UCallExpression expression = UastContextKt.toUElement(element, UCallExpression.class);
      if (expression == null) {
        continue;
      }

      // Only calls with arguments may have string literal
      if (expression.getValueArgumentCount() < 1) continue;

      UIdentifier identifier = expression.getMethodIdentifier();
      if (identifier == null) continue;
      PsiElement sourcePsi = identifier.getSourcePsi();
      if (sourcePsi == null) continue;

      ProgressManager.checkCanceled();

      UExpression argument = expression.getValueArguments().get(0);
      if (!canBeStringConstant(argument)) {
        continue;
      }

      PsiType expressionType = expression.getExpressionType();
      if (!(expressionType instanceof PsiClassType)) continue;
      if (uniqueTypes.add((PsiClassType)expressionType) &&
          expressionType.isValid() &&
          ProjectIconsAccessor.isIconClassType(expressionType)) {
        applicableTypes.add((PsiClassType)expressionType);
      }

      if (!applicableTypes.contains(expressionType)) {
        continue;
      }

      UValue uValue = UEvaluationContextKt.uValueOf(argument);
      if (uValue != null) {
        Collection<UExpression> constants = new ArrayList<>();
        for (UConstant constant : UValueKt.toPossibleConstants(uValue)) {
          if (constant instanceof UStringConstant) {
            UExpression source = constant.getSource();
            constants.add(source);
          }
        }
        if (!constants.isEmpty()) {
          LineMarkerInfo<PsiElement> marker = createIconLineMarker(ContainerUtil.getFirstItem(constants), sourcePsi);
          if (marker != null) {
            result.add(marker);
          }
        }
      }
    }
  }

  private static boolean canBeStringConstant(@NotNull UExpression expression) {
    if (expression instanceof UPolyadicExpression ||
        expression instanceof ULiteralExpression ||
        expression instanceof UReferenceExpression ||
        expression instanceof UInjectionHost) {
      return true;
    }

    if (expression instanceof UUnaryExpression uUnaryExpression) {
      return canBeStringConstant(uUnaryExpression.getOperand());
    }

    if (expression instanceof UParenthesizedExpression uParenthesizedExpression) {
      return canBeStringConstant(uParenthesizedExpression.getExpression());
    }

    return false;
  }

  private static @Nullable LineMarkerInfo<PsiElement> createIconLineMarker(@Nullable UExpression initializer, PsiElement bindingElement) {
    if (initializer == null) {
      return null;
    }

    Project project = bindingElement.getProject();
    ProjectIconsAccessor iconsAccessor = ProjectIconsAccessor.getInstance(project);
    VirtualFile file = iconsAccessor.resolveIconFile(initializer);
    if (file == null) {
      return null;
    }

    Icon icon = iconsAccessor.getIcon(file);
    if (icon == null) {
      return null;
    }

    GutterIconNavigationHandler<PsiElement> navHandler = (e, elt) -> FileEditorManager.getInstance(project).openFile(file, true);
    return new LineMarkerInfo<>(bindingElement, bindingElement.getTextRange(), icon,
                                null, navHandler,
                                GutterIconRenderer.Alignment.LEFT);
  }
}
