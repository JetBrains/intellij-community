/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.diagnostic.DebugLogManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class DebugLogConfigureAction extends DumbAwareAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject() == null ? ProjectManager.getInstance().getDefaultProject() : e.getProject();
    DebugLogManager logCustomizer = ServiceManager.getService(DebugLogManager.class);
    DebugLogConfigureDialog dialog = new DebugLogConfigureDialog(project, logCustomizer.getSavedCategories());
    if (dialog.showAndGet()) {
      List<String> categories = dialog.getLogCategories();
      logCustomizer.applyCategories(categories);
      logCustomizer.saveCategories(categories);
    }
  }

  private static class DebugLogConfigureDialog extends DialogWrapper {

    private static final String ALL_POSSIBLE_SEPARATORS = "(\n|,|;)+";
    @NotNull private final JTextArea myTextArea;

    protected DebugLogConfigureDialog(@Nullable Project project, List<String> categories) {
      super(project, false);
      myTextArea = new JTextArea(10, 30);
      myTextArea.setText(StringUtil.join(categories, "\n"));
      setTitle("Custom Debug Log Configuration");
      init();
    }

    @Nullable
    @Override
    protected JComponent createNorthPanel() {
      return new JBLabel("Add log categories separated by new lines");
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return ScrollPaneFactory.createScrollPane(myTextArea);
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myTextArea;
    }

    @NotNull
    public List<String> getLogCategories() {
      return parseCategories(myTextArea.getText());
    }

    @NotNull
    private static List<String> parseCategories(@NotNull String text) {
      return ContainerUtil.mapNotNull(text.split(ALL_POSSIBLE_SEPARATORS), new Function<String, String>() {
        @Override
        public String fun(String s) {
          return StringUtil.isEmptyOrSpaces(s) ? null : s.trim();
        }
      });
    }
  }

}
