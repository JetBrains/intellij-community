/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.*;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.ProjectIconsAccessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

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
    if (element instanceof PsiAssignmentExpression) {
      final PsiExpression lExpression = ((PsiAssignmentExpression)element).getLExpression();
      final PsiExpression expr = ((PsiAssignmentExpression)element).getRExpression();
      if (lExpression instanceof PsiReferenceExpression) {
        PsiElement var = ((PsiReferenceExpression)lExpression).resolve();
        if (var instanceof PsiVariable) {
          return createIconLineMarker(((PsiVariable)var).getType(), expr);
        }
      }
    }
    else if (element instanceof PsiReturnStatement) {
      PsiReturnStatement psiReturnStatement = (PsiReturnStatement)element;
      final PsiExpression value = psiReturnStatement.getReturnValue();
      final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (method != null) {
        final PsiType returnType = method.getReturnType();
        final LineMarkerInfo<PsiElement> result = createIconLineMarker(returnType, value);

        if (result != null || !ProjectIconsAccessor.isIconClassType(returnType) || value == null) return result;

        if (methodContainsReturnStatementOnly(method)) {
          for (PsiReference ref : value.getReferences()) {
            final PsiElement field = ref.resolve();
            if (field instanceof PsiField) {
              return createIconLineMarker(returnType, ((PsiField)field).getInitializer(), psiReturnStatement);
            }
          }
        }
      }
    }
    else if (element instanceof PsiVariable) {
      PsiVariable var = (PsiVariable)element;

      PsiUtilCore.ensureValid(var);
      final PsiType type = var.getType();
      if (!type.isValid()) {
        PsiUtil.ensureValidType(type, "in variable: " + var + " of " + var.getClass());
      }

      return createIconLineMarker(type, var.getInitializer());
    }
    return null;
  }

  private static boolean methodContainsReturnStatementOnly(@NotNull PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    if (body == null || body.getStatements().length != 1) return false;

    return body.getStatements()[0] instanceof PsiReturnStatement;
  }

  @Nullable
  private static LineMarkerInfo<PsiElement> createIconLineMarker(PsiType type, @Nullable PsiExpression initializer) {
    return createIconLineMarker(type, initializer, initializer);
  }

  @Nullable
  private static LineMarkerInfo<PsiElement> createIconLineMarker(PsiType type,
                                                                 @Nullable PsiExpression initializer,
                                                                 PsiElement bindingElement) {
    if (initializer == null) return null;

    final Project project = initializer.getProject();

    final VirtualFile file = ProjectIconsAccessor.getInstance(project).resolveIconFile(type, initializer);
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
