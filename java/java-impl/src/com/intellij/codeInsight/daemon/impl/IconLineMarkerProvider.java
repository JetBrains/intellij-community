/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.ProjectIconsAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.evaluation.UEvaluationContextKt;
import org.jetbrains.uast.values.*;

import javax.swing.*;
import java.awt.event.MouseEvent;
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
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
  }

  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
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
      if (arguments.size() > 0) {
        Collection<UExpression> constants = UValueKt.toPossibleConstants(arguments.get(0))
          .stream()
          .filter(constant -> constant instanceof UStringConstant)
          .map(UConstant::getSource)
          .collect(Collectors.toList());
        List<PsiElement> psiElements = UastUtils.toPsiElements(constants);
        if (psiElements.size() > 0) {
          UIdentifier identifier = expression.getMethodIdentifier();
          if (identifier != null) {
            return createIconLineMarker(psiElements.get(0), identifier.getPsi());
          }
        }
      }
    }

    return null;
  }
  @Nullable
  private static LineMarkerInfo<PsiElement> createIconLineMarker(@Nullable PsiElement initializer,
                                                                 PsiElement bindingElement) {
    if (initializer == null) return null;

    final Project project = initializer.getProject();

    final VirtualFile file = ProjectIconsAccessor.getInstance(project).resolveIconFile(initializer);
    if (file == null) return null;

    final Icon icon = ProjectIconsAccessor.getInstance(project).getIcon(file);
    if (icon == null) return null;

    final GutterIconNavigationHandler<PsiElement> navHandler = new GutterIconNavigationHandler<PsiElement>() {
      @Override
      public void navigate(MouseEvent e, PsiElement elt) {
        FileEditorManager.getInstance(project).openFile(file, true);
      }
    };

    return new LineMarkerInfo<PsiElement>(bindingElement, bindingElement.getTextRange(), icon,
                                          Pass.LINE_MARKERS, null, navHandler,
                                          GutterIconRenderer.Alignment.LEFT);
  }

  @NotNull
  @Override
  public String getName() {
    return "Icon preview";
  }

  @Override
  public boolean isEnabledByDefault() {
    return DaemonCodeAnalyzerSettings.getInstance().SHOW_SMALL_ICONS_IN_GUTTER;
  }
}
