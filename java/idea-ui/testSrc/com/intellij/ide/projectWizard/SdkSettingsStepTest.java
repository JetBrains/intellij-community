// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SdkSettingsStep;
import com.intellij.idea.TestFor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox.JdkComboBoxItem;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SdkSettingsStepTest extends NewProjectWizardTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    WriteAction.runAndWait(() -> {
      ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
      for (Sdk jdk : jdkTable.getAllJdks()) {
        jdkTable.removeJdk(jdk);
      }
    });
  }

  protected abstract class TestScope {
    SdkSettingsStep mySdkStep;
    JdkComboBox myJdkComboBox;

    TestScope() throws Exception {
      createProject(step -> {
        if (!(step instanceof ProjectTypeStep)) return;
        ProjectTypeStep typeStep = (ProjectTypeStep)step;
        assertThat(typeStep.setSelectedTemplate("Java", null)).isTrue();

        ModuleWizardStep firstStep = typeStep.getSettingsStep();
        assertThat(firstStep).isInstanceOf(SdkSettingsStep.class);

        mySdkStep = (SdkSettingsStep)firstStep;

        myJdkComboBox = UIUtil.uiTraverser(mySdkStep.getComponent()).filter(JdkComboBox.class).single();
        assertThat(myJdkComboBox).isNotNull();

        doTest();

        cancelWizardRun();
      });
    }

    protected abstract void doTest();

    @NotNull
    List<JdkComboBoxItem> getJdkComboBoxItems() {
      List<JdkComboBoxItem> result = new ArrayList<>();
      ComboBoxModel<JdkComboBoxItem> model = myJdkComboBox.getModel();
      for(int i = 0; i < model.getSize(); i++) {
        result.add(model.getElementAt(i));
      }
      return result;
    }
  }

  @TestFor(issues = "IDEA-234381")
  public void testNoProjectSDKShown() throws Exception {
    new TestScope() {
      @Override
      protected void doTest() {
        List<JdkComboBoxItem> boxItems = getJdkComboBoxItems();

        //project SDK should not appear here
        assertThat(boxItems).noneMatch(JdkComboBox.ProjectJdkComboBoxItem.class::isInstance);
        assertThat(myJdkComboBox.getSelectedJdk()).isNull();
      }
    };
  }

  @TestFor(issues = "IDEA-234381")
  public void testWizardShouldPrefExistingJDK() throws Exception {
    Project project = getProject();
    Sdk jdk11 = IdeaTestUtil.getMockJdk(JavaVersion.parse("11"));
    Sdk jdk13 = IdeaTestUtil.getMockJdk(JavaVersion.parse("13"));
    Sdk jdk8 = IdeaTestUtil.getMockJdk(JavaVersion.parse("8"));

    WriteAction.runAndWait(() -> {
      ProjectJdkTable.getInstance().addJdk(jdk11, project);
      ProjectJdkTable.getInstance().addJdk(jdk8, project);
      ProjectJdkTable.getInstance().addJdk(jdk13, project);
    });

    new TestScope() {
      @Override
      protected void doTest() {
        List<JdkComboBoxItem> boxItems = getJdkComboBoxItems();

        //project SDK should not appear here
        assertThat(boxItems).noneMatch(JdkComboBox.ProjectJdkComboBoxItem.class::isInstance);
        assertThat(boxItems).noneMatch(JdkComboBox.NoneJdkComboBoxItem.class::isInstance);
        assertThat(boxItems).anyMatch(JdkComboBox.ActualJdkComboBoxItem.class::isInstance);

        assertThat(myJdkComboBox.getSelectedJdk())
          .isNotNull()
          .extracting(it -> it.getVersionString()).isEqualTo(jdk13.getVersionString());
      }
    };
  }
}
