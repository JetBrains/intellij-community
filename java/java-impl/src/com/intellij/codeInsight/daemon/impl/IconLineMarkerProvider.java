// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shows small (16x16 or less) icons as gutters.
 * <p/>
 * Works in places where it's possible to resolve from literal expression
 * to an icon image.
 *
 * @author Konstantin Bulenkov
 */
public class IconLineMarkerProvider extends LineMarkerProviderDescriptor {
  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
    UCallExpression expression = UastContextKt.toUElement(element, UCallExpression.class);
    if (expression == null) {
      return null;
    }

    if (!ProjectIconsAccessor.isIconClassType(expression.getExpressionType())) {
      return null;
    }

    UValue uValue = UEvaluationContextKt.uValueOf(expression);
    if (uValue instanceof UCallResultValue) {
      List<UValue> arguments = ((UCallResultValue)uValue).getArguments();
      if (!arguments.isEmpty()) {
        Collection<UExpression> constants = UValueKt.toPossibleConstants(arguments.get(0))
          .stream()
          .filter(constant -> constant instanceof UStringConstant)
          .map(UConstant::getSource)
          .collect(Collectors.toList());
        if (!constants.isEmpty()) {
          UIdentifier identifier = expression.getMethodIdentifier();
          if (identifier != null) {
            return createIconLineMarker(ContainerUtil.getFirstItem(constants), identifier.getPsi());
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private static LineMarkerInfo<PsiElement> createIconLineMarker(@Nullable UExpression initializer,
                                                                 PsiElement bindingElement) {
    if (initializer == null) return null;

    final Project project = bindingElement.getProject();

    final ProjectIconsAccessor iconsAccessor = ProjectIconsAccessor.getInstance(project);

    final VirtualFile file = iconsAccessor.resolveIconFile(initializer);
    if (file == null) return null;

    final Icon icon = iconsAccessor.getIcon(file);
    if (icon == null) return null;

    final GutterIconNavigationHandler<PsiElement> navHandler = (e, elt) -> FileEditorManager.getInstance(project).openFile(file, true);

    return new LineMarkerInfo<>(bindingElement, bindingElement.getTextRange(), icon,
                                null, navHandler,
                                GutterIconRenderer.Alignment.LEFT);
  }

  @NotNull
  @Override
  public String getName() {
    return JavaBundle.message("icon.preview");
  }
}
