/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.FoldingDescriptorWithCustomRenderer;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.ui.JBColor;
import com.intellij.util.FontUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class JavaFoldingBuilder extends JavaFoldingBuilderBase {
  private static final Logger LOG = Logger.getInstance(JavaFoldingBuilder.class);

  @Override
  protected boolean isBelowRightMargin(@NotNull Project project, int lineLength) {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
    return lineLength <= settings.getRightMargin(JavaLanguage.INSTANCE);
  }

  @Override
  protected boolean shouldShowExplicitLambdaType(@NotNull PsiAnonymousClass anonymousClass, @NotNull PsiNewExpression expression) {
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiReferenceExpression || parent instanceof PsiAssignmentExpression) {
      return true;
    }

    ExpectedTypeInfo[] types = ExpectedTypesProvider.getExpectedTypes(expression, false);
    return types.length != 1 || !types[0].getType().equals(anonymousClass.getBaseClassType());
  }

  @Override
  @NotNull
  protected String rightArrow() {
    return getRightArrow();
  }

  @NotNull
  public static String getRightArrow() {
    Font font = EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN);
    return FontUtil.rightArrow(font);
  }

  @Override
  protected boolean addFoldRegion(List<FoldingDescriptor> list,
                                  PsiElement elementToFold,
                                  Document document,
                                  boolean allowOneLiners,
                                  TextRange range, boolean quick) {
    if (!quick && elementToFold instanceof PsiDocComment && ((PsiDocComment)elementToFold).getOwner() != null) {
      try {
        String text = JavaDocumentationProvider.generateExternalJavadoc(((PsiDocComment)elementToFold).getOwner());
        list.add(new FoldingDescriptorWithCustomRenderer(elementToFold, range, new Inlay.Renderer() {
          private int width;
          private int height;
          private JEditorPane pane;

          @Override
          public void paint(@NotNull Graphics g, @NotNull Rectangle r, @NotNull Editor editor) {
            calcEverything(editor);
            Graphics localG = g.create(r.x, r.y, r.width, r.height);
            pane.paint(localG);
            localG.dispose();
          }

          @Override
          public int calcWidthInPixels(@NotNull Editor editor) {
            calcEverything(editor);
            return width;
          }

          @Override
          public int calcHeightInPixels(@NotNull Editor editor) {
            calcEverything(editor);
            return height;
          }

          private void calcEverything(@NotNull Editor editor) {
            if (pane == null) {
              pane = new JEditorPane(UIUtil.HTML_MIME, "");
              pane.setEditorKit(UIUtil.getHTMLEditorKit(false));
              pane.setText(text);
              pane.setBackground(((FoldingModelEx)editor.getFoldingModel()).getPlaceholderAttributes().getBackgroundColor());
              pane.setBorder(BorderFactory.createLineBorder(JBColor.GRAY));
              width = editor.getSettings().getRightMargin(editor.getProject()) * EditorUtil.getPlainSpaceWidth(editor);
              pane.setSize(width, Integer.MAX_VALUE);
              height = pane.getPreferredSize().height;
              pane.setSize(width, height);
            }
          }
        }));
        return true;
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }
    return super.addFoldRegion(list, elementToFold, document, allowOneLiners, range, quick);
  }
}

