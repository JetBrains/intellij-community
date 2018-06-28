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
package com.intellij.psi.codeStyle.extractor.ui;

import com.intellij.application.options.CodeStyle;
import com.intellij.diff.DiffContentFactoryEx;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.extractor.differ.LangCodeStyleExtractor;
import com.intellij.psi.codeStyle.extractor.values.ValuesExtractionResult;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ExtractPreviewDialog extends DialogWrapper {
  @NotNull final PsiFile myFile;
  @NotNull final ValuesExtractionResult myExtractionResult;
  @NotNull final String myReport;
  
  public ExtractPreviewDialog(@NotNull PsiFile file, @NotNull ValuesExtractionResult result, @NotNull String report) {
    super(file.getProject());
    myFile = file;
    myExtractionResult = result;
    myReport = report;
    setTitle(String.format("Extracted from the %s file code style", file.getName()));
    setOKButtonText("Accept Extracted Code Style As Project default");
    //setCancelButtonText("Keep Original Code Style");
    init();
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    return new JLabel("<html>" + myReport + "</html>");
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final LangCodeStyleExtractor extractor = LangCodeStyleExtractor.EXTENSION.forLanguage(myFile.getLanguage());
    final CodeStyleSettings settings = CodeStyle.getSettings(myFile);

    final ValuesExtractionResult orig = myExtractionResult.apply(true);
    final Project project = myFile.getProject();
    final String reformattedText = extractor.getDiffer(project, myFile, settings).reformattedText();
    orig.apply(false);

    DiffContentFactoryEx contentFactory = DiffContentFactoryEx.getInstanceEx();
    final DiffContent origCtx = contentFactory.create(project, myFile.getVirtualFile());
    final DiffContent newCtx = contentFactory.create(project, reformattedText);

    ContentDiffRequest request = new SimpleDiffRequest(null, origCtx, newCtx, "Original Code", "Code reformatted with Extracted Code Style");

    DiffRequestPanel diffPanel = DiffManager.getInstance().createRequestPanel(project, getDisposable(), null);
    diffPanel.setRequest(request);

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(diffPanel.getComponent(), BorderLayout.CENTER);
    panel.setBorder(IdeBorderFactory.createEmptyBorder(JBUI.insetsTop(5)));
    return panel;
  }
}
