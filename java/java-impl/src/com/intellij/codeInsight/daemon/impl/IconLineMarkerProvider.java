// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.ProjectIconsAccessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UIdentifier;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.evaluation.UEvaluationContextKt;
import org.jetbrains.uast.values.*;

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
    if (!hasIconTypeExpressions(elements)) {
      return;
    }

    for (PsiElement element : elements) {
      UCallExpression expression = UastContextKt.toUElement(element, UCallExpression.class);
      if (expression == null) {
        continue;
      }

      if (!ProjectIconsAccessor.isIconClassType(expression.getExpressionType())) {
        continue;
      }

      UValue uValue = UEvaluationContextKt.uValueOf(expression);
      if (!(uValue instanceof UCallResultValue)) {
        continue;
      }

      List<UValue> arguments = ((UCallResultValue)uValue).getArguments();
      if (!arguments.isEmpty()) {
        Collection<UExpression> constants = new ArrayList<>();
        for (UConstant constant : UValueKt.toPossibleConstants(arguments.get(0))) {
          if (constant instanceof UStringConstant) {
            UExpression source = constant.getSource();
            constants.add(source);
          }
        }
        if (!constants.isEmpty()) {
          UIdentifier identifier = expression.getMethodIdentifier();
          if (identifier != null && identifier.getSourcePsi() != null) {
            LineMarkerInfo<PsiElement> marker = createIconLineMarker(ContainerUtil.getFirstItem(constants), identifier.getSourcePsi());
            if (marker != null) {
              result.add(marker);
            }
          }
        }
      }
    }
  }

  private static boolean hasIconTypeExpressions(@NotNull List<? extends PsiElement> elements) {
    Set<PsiType> uniqueValues = new HashSet<>();
    for (PsiElement e : elements) {
      UCallExpression element = UastContextKt.toUElement(e, UCallExpression.class);
      if (element != null) {
        PsiType type = element.getExpressionType();
        if (uniqueValues.add(type)) {
          if (ProjectIconsAccessor.isIconClassType(type)) {
            return true;
          }
        }
      }
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
