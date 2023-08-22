// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ui.CustomComponentExtensionWithSwingRenderer;
import com.intellij.codeInspection.ui.UiDslOptPaneRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightPlatform4TestCase;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import javax.swing.*;

import static com.intellij.codeInspection.options.OptPane.pane;

public class OptCustomTest extends LightPlatform4TestCase {
  private static class MyInspection extends LocalInspectionTool {
    @Override
    public @NotNull OptPane getOptionsPane() {
      return pane(
        new OptCustom("x", "2"),
        new OptCustom("y")
      );
    }
  }
  
  @Test
  public void customControls() {
    CustomComponentExtension.EP_NAME.getPoint().registerExtension(
      new CustomComponentExtensionWithSwingRenderer<String>("x") {
        @Override
        public @NotNull String serializeData(String s) {
          return s;
        }

        @Override
        public String deserializeData(@NotNull String data) {
          return data;
        }

        @Override
        public @NotNull JComponent render(String data, @NotNull Project project) {
          return new JLabel(data);
        }
      }, getTestRootDisposable());
    CustomComponentExtension.EP_NAME.getPoint().registerExtension(
      new CustomComponentExtensionWithSwingRenderer<Void>("y") {
        @Override
        public @NotNull JComponent render(Void data, @NotNull Project project) {
          return new JButton("3");
        }
      }, getTestRootDisposable());
    MyInspection inspection = new MyInspection();
    JComponent component = new UiDslOptPaneRenderer().render(inspection.getOptionController(), 
                                                             inspection.getOptionsPane(), getTestRootDisposable(), getProject());
    JButton button = UIUtil.findComponentOfType(component, JButton.class);
    assertEquals("3", button.getText());
    JLabel label = UIUtil.findComponentOfType(component, JLabel.class);
    assertEquals("2", label.getText());
  }
}
