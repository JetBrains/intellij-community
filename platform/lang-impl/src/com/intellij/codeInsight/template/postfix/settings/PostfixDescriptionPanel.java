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
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.impl.config.ActionUsagePanel;
import com.intellij.codeInsight.intention.impl.config.PlainTextDescriptor;
import com.intellij.codeInsight.intention.impl.config.TextDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

class PostfixDescriptionPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionDescriptionPanel");
  private JPanel myPanel;

  private JPanel myAfterPanel;
  private JPanel myBeforePanel;
  private JEditorPane myDescriptionBrowser;

  public PostfixDescriptionPanel() {
    myDescriptionBrowser.setMargin(new Insets(5, 5, 5, 5));
    initializeExamplePanel(myAfterPanel);
    initializeExamplePanel(myBeforePanel);
  }


  public void reset(@NotNull PostfixTemplateMetaData actionMetaData) {
    boolean isEmpty = actionMetaData == PostfixTemplateMetaData.EMPTY_METADATA;
    myDescriptionBrowser.setText(isEmpty ? CodeInsightBundle.message("templates.postfix.settings.category.text")
                                         : getDescription(actionMetaData.getDescription()));

    showUsages(myBeforePanel, isEmpty
                              ? new PlainTextDescriptor(CodeInsightBundle.message("templates.postfix.settings.category.before"),
                                                        "before.txt.template")
                              : ArrayUtil.getFirstElement(actionMetaData.getExampleUsagesBefore()));
    showUsages(myAfterPanel, isEmpty
                             ? new PlainTextDescriptor(CodeInsightBundle.message("templates.postfix.settings.category.after"),
                                                       "after.txt.template")
                             : ArrayUtil.getFirstElement(actionMetaData.getExampleUsagesAfter()));
  }


  @NotNull
  private static String getDescription(TextDescriptor url) {
    try {
      return url.getText();
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return "";
  }

  private static void showUsages(@NotNull JPanel panel, @Nullable TextDescriptor exampleUsage) {
    String text = "";
    FileType fileType = PlainTextFileType.INSTANCE;
    if (exampleUsage != null) {
      try {
        text = exampleUsage.getText();
        String name = exampleUsage.getFileName();
        FileTypeManagerEx fileTypeManager = FileTypeManagerEx.getInstanceEx();
        String extension = fileTypeManager.getExtension(name);
        fileType = fileTypeManager.getFileTypeByExtension(extension);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    ((ActionUsagePanel)panel.getComponent(0)).reset(text, fileType);
    panel.repaint();
  }

  private void initializeExamplePanel(@NotNull JPanel panel) {
    panel.setLayout(new BorderLayout());
    ActionUsagePanel actionUsagePanel = new ActionUsagePanel();
    panel.add(actionUsagePanel);
    Disposer.register(this, actionUsagePanel);
  }

  synchronized JPanel getComponent() {
    return myPanel;
  }

  @Override
  public void dispose() {
  }
}
