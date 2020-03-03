// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SdkSettingsStep;
import com.intellij.idea.TestFor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox.JdkComboBoxItem;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SdkSettingsStepTest extends NewProjectWizardTestCase {

  @TestFor(issues = "IDEA-234381")
  public void testNoProjectSDKShown() throws IOException {

    WriteAction.runAndWait(() -> {
      ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
      for (Sdk jdk : jdkTable.getAllJdks()) {
        jdkTable.removeJdk(jdk);
      }
    });

    createProject(step -> {
      if (!(step instanceof ProjectTypeStep)) return;
      ProjectTypeStep typeStep = (ProjectTypeStep)step;
      assertThat(typeStep.setSelectedTemplate("Java", null)).isTrue();

      ModuleWizardStep firstStep = typeStep.getSettingsStep();
      assertThat(firstStep).isInstanceOf(SdkSettingsStep.class);

      SdkSettingsStep sdkStep = (SdkSettingsStep)firstStep;

      JdkComboBox jdkComboBox = UIUtil.uiTraverser(sdkStep.getComponent()).filter(JdkComboBox.class).single();
      assertThat(jdkComboBox).isNotNull();

      List<JdkComboBoxItem> boxItems = getItems(jdkComboBox);

      for (JdkComboBoxItem item : boxItems) {
        System.out.println("SDK Item: " + item + ", " + item.getClass().getName());
      }

      //project SDK should not appear here
      assertThat(boxItems).noneMatch(JdkComboBox.ProjectJdkComboBoxItem.class::isInstance);
      assertThat(jdkComboBox.getSelectedJdk()).isNull();
    });
  }

  @NotNull
  private static List<JdkComboBoxItem> getItems(@NotNull JdkComboBox comboBox) {
    List<JdkComboBoxItem> result = new ArrayList<>();
    ComboBoxModel<JdkComboBoxItem> model = comboBox.getModel();
    for(int i = 0; i < model.getSize(); i++) {
      result.add(model.getElementAt(i));
    }
    return result;
  }
}
