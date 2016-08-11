/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.application.options.CodeStyleSchemesConfigurable;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class LongLineInspection extends LocalInspectionTool {

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final HyperlinkLabel codeStyleHyperlink = new HyperlinkLabel("Edit Code Style settings");
    codeStyleHyperlink.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        DataManager.getInstance().getDataContextFromFocus().doWhenDone(new Consumer<DataContext>() {
          @Override
          public void consume(DataContext context) {
            if (context != null) {
              final Settings settings = Settings.KEY.getData(context);
              if (settings != null) {
                settings.select(settings.find(CodeStyleSchemesConfigurable.class));
              }
              else {
                ShowSettingsUtil.getInstance()
                  .showSettingsDialog(CommonDataKeys.PROJECT.getData(context), CodeStyleSchemesConfigurable.class);
              }
            }
          }
        });
      }
    });
    final JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(codeStyleHyperlink, BorderLayout.NORTH);
    return panel;
  }

  @Nullable
  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final Project project = manager.getProject();
    final int codeStyleRightMargin = CodeStyleSettingsManager.getSettings(project).getRightMargin(file.getLanguage());
    final VirtualFile vFile = file.getVirtualFile();
    if (vFile instanceof VirtualFileWindow) {
      return null;
    }
    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    if (document == null) {
      return null;
    }
    final List<ProblemDescriptor> descriptors = new SmartList<>();
    for (int idx = 0; idx < document.getLineCount(); idx++) {
      final int startOffset = document.getLineStartOffset(idx);
      final int endOffset = document.getLineEndOffset(idx);
      if (endOffset - startOffset > codeStyleRightMargin) {
        final int maxOffset = startOffset + codeStyleRightMargin;
        descriptors.add(
          manager.createProblemDescriptor(file, new TextRange(maxOffset, endOffset),
                                          String.format("Line is longer than allowed by code style (> %s columns)", codeStyleRightMargin),
                                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                          isOnTheFly));
      }
    }
    return descriptors.isEmpty() ? null : descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }
}
