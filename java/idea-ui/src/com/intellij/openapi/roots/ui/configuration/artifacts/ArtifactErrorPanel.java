/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ConfigurationErrorQuickFix;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactErrorPanel {
  private JPanel myMainPanel;
  private JButton myFixButton;
  private JLabel myErrorLabel;
  private List<? extends ConfigurationErrorQuickFix> myCurrentQuickFixes;
  private String myErrorText;

  public ArtifactErrorPanel(final ArtifactEditorImpl artifactEditor) {
    myErrorLabel.setIcon(AllIcons.RunConfigurations.ConfigurationWarning);
    new UiNotifyConnector(myMainPanel, new Activatable.Adapter() {
      @Override
      public void showNotify() {
        if (myErrorText != null) {
          myErrorLabel.setText(myErrorText);
          myErrorText = null;
        }
      }
    });
    myFixButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!myCurrentQuickFixes.isEmpty()) {
          if (myCurrentQuickFixes.size() == 1) {
            performFix(ContainerUtil.getFirstItem(myCurrentQuickFixes, null), artifactEditor);
          }
          else {
            JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<ConfigurationErrorQuickFix>(null, myCurrentQuickFixes) {
              @NotNull
              @Override
              public String getTextFor(ConfigurationErrorQuickFix value) {
                return value.getActionName();
              }

              @Override
              public PopupStep onChosen(ConfigurationErrorQuickFix selectedValue, boolean finalChoice) {
                performFix(selectedValue, artifactEditor);
                return FINAL_CHOICE;
              }
            }).showUnderneathOf(myFixButton);
          }
        }
      }
    });
    clearError();
  }

  private static void performFix(ConfigurationErrorQuickFix quickFix, ArtifactEditorImpl artifactEditor) {
    quickFix.performFix();
    artifactEditor.queueValidation();
  }

  public void showError(@NotNull String message, @NotNull List<? extends ConfigurationErrorQuickFix> quickFixes) {
    myErrorLabel.setVisible(true);
    final String errorText = XmlStringUtil.wrapInHtml(message);
    if (myErrorLabel.isShowing()) {
      myErrorLabel.setText(errorText);
    }
    myErrorText = errorText;
    myMainPanel.setVisible(true);
    myCurrentQuickFixes = quickFixes;
    myFixButton.setVisible(!quickFixes.isEmpty());
    if (!quickFixes.isEmpty()) {
      myFixButton.setText(quickFixes.size() == 1 ? ContainerUtil.getFirstItem(quickFixes, null).getActionName() : IdeBundle.message("button.fix"));
    }
  }

  public void clearError() {
    myErrorText = null;
    myMainPanel.setVisible(false);
    myErrorLabel.setVisible(false);
    myFixButton.setVisible(false);
  }

  public JComponent getMainPanel() {
    return myMainPanel;
  }
}
