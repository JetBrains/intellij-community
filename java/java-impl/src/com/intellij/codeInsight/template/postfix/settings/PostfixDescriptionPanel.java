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

import com.intellij.codeInsight.intention.impl.config.ActionUsagePanel;
import com.intellij.codeInsight.intention.impl.config.TextDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.ArrayUtil;
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


  public void reset(PostfixTemplateMetaData actionMetaData) {
    try {
      final TextDescriptor url = actionMetaData.getDescription();
      final String description = url == null ? "" : url.getText();
      myDescriptionBrowser.setText(description);

      showUsages(myBeforePanel, ArrayUtil.getFirstElement(actionMetaData.getExampleUsagesBefore()));
      showUsages(myAfterPanel, ArrayUtil.getFirstElement(actionMetaData.getExampleUsagesAfter()));
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void showUsages(final JPanel panel, @Nullable final TextDescriptor exampleUsage) throws IOException {
    boolean createMode = exampleUsage == null || panel.getComponents().length == 0;
    if (createMode) {
      panel.setLayout(new BorderLayout());
      panel.removeAll();
    }

    if (exampleUsage != null) {
      final String name = exampleUsage.getFileName();
      final FileTypeManagerEx fileTypeManager = FileTypeManagerEx.getInstanceEx();
      final String extension = fileTypeManager.getExtension(name);
      final FileType fileType = fileTypeManager.getFileTypeByExtension(extension);

      ActionUsagePanel actionUsagePanel;
      if (createMode) {
        actionUsagePanel = new ActionUsagePanel();
        Disposer.register(this, actionUsagePanel);
      }
      else {
        actionUsagePanel = (ActionUsagePanel)panel.getComponent(0);
      }

      actionUsagePanel.reset(exampleUsage.getText(), fileType);

      if (createMode) {
        panel.add(actionUsagePanel);
      }
    }
    panel.repaint();
  }

  JPanel getComponent() {
    return myPanel;
  }

  @Override
  public void dispose() {
  }

  public void init(final int preferredWidth) {
    double height =
      (myDescriptionBrowser.getSize().getHeight() + myBeforePanel.getSize().getHeight() + myAfterPanel.getSize().getHeight()) / 3;
    Dimension newd = new Dimension(preferredWidth, (int)height);
    myDescriptionBrowser.setSize(newd);
    myDescriptionBrowser.setPreferredSize(newd);
    myDescriptionBrowser.setMaximumSize(newd);
    myDescriptionBrowser.setMinimumSize(newd);

    myBeforePanel.setSize(newd);
    myBeforePanel.setPreferredSize(newd);
    myBeforePanel.setMaximumSize(newd);
    myBeforePanel.setMinimumSize(newd);

    myAfterPanel.setSize(newd);
    myAfterPanel.setPreferredSize(newd);
    myAfterPanel.setMaximumSize(newd);
    myAfterPanel.setMinimumSize(newd);
  }
}
