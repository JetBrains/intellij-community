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
package com.intellij.openapi.externalSystem.service.ui;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class ExternalSystemJdkComboBox extends ComboBoxWithWidePopup<ExternalSystemJdkComboBox.JdkComboBoxItem> {

  private static final int MAX_PATH_LENGTH = 50;

  @Nullable
  private Project myProject;
  private boolean suggestJre = true;

  public ExternalSystemJdkComboBox() {
    this(null);
  }

  public ExternalSystemJdkComboBox(@Nullable Project project) {
    myProject = project;
    setRenderer(new ColoredListCellRenderer<JdkComboBoxItem>() {

      @Override
      protected void customizeCellRenderer(@NotNull JList list, JdkComboBoxItem value, int index, boolean selected, boolean hasFocus) {
        CompositeAppearance appearance = new CompositeAppearance();
        SdkType sdkType = JavaSdk.getInstance();
        appearance.setIcon(sdkType.getIcon());
        SimpleTextAttributes attributes = getTextAttributes(value.valid, selected);
        CompositeAppearance.DequeEnd ending = appearance.getEnding();

        ending.addText(value.label, attributes);
        if (value.comment != null && !value.comment.equals(value.jdkName)) {
          final SimpleTextAttributes textAttributes;
          if (!value.valid) {
            textAttributes = SimpleTextAttributes.ERROR_ATTRIBUTES;
          }
          else {
            textAttributes = SystemInfo.isMac && selected
                             ? new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.WHITE)
                             : SimpleTextAttributes.GRAY_ATTRIBUTES;
          }

          ending.addComment(value.comment, textAttributes);
        }

        final CompositeAppearance compositeAppearance = ending.getAppearance();
        compositeAppearance.customize(this);
      }
    });
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  public void setProject(@Nullable Project project) {
    myProject = project;
  }

  public void setSetupButton(@NotNull JButton setUpButton,
                             @NotNull ProjectSdksModel jdksModel,
                             @Nullable String actionGroupTitle,
                             @Nullable Condition<SdkTypeId> creationFilter) {
    setSetupButton(setUpButton, jdksModel, actionGroupTitle, creationFilter, null);
  }

  public void setSetupButton(@NotNull JButton setUpButton,
                             @NotNull ProjectSdksModel jdksModel,
                             @Nullable String actionGroupTitle,
                             @Nullable Condition<SdkTypeId> creationFilter,
                             @Nullable WizardContext wizardContext) {
    Arrays.stream(setUpButton.getActionListeners()).forEach(setUpButton::removeActionListener);

    setUpButton.addActionListener(e -> {
      DefaultActionGroup group = new DefaultActionGroup();
      jdksModel.createAddActions(group, this, getSelectedJdk(), jdk -> {
        Sdk existingJdk = Arrays.stream(ProjectJdkTable.getInstance().getAllJdks())
          .filter(sdk -> StringUtil.equals(sdk.getHomePath(), jdk.getHomePath()))
          .findFirst().orElse(null);

        String jdkName;
        if (existingJdk == null) {
          SdkConfigurationUtil.addSdk(jdk);
          jdkName = jdk.getName();
        }
        else {
          jdkName = existingJdk.getName();
        }
        refreshData(jdkName, wizardContext != null ? wizardContext.getProjectJdk() : null);
      }, creationFilter);
      final DataContext dataContext = DataManager.getInstance().getDataContext(this);
      if (group.getChildrenCount() > 1) {
        JBPopupFactory.getInstance()
          .createActionGroupPopup(actionGroupTitle, group, dataContext, JBPopupFactory.ActionSelectionAid.MNEMONICS, false)
          .showUnderneathOf(setUpButton);
      }
      else {
        final AnActionEvent event =
          new AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, new Presentation(""), ActionManager.getInstance(), 0);
        group.getChildren(event)[0].actionPerformed(event);
      }
    });
  }

  @Nullable
  public Sdk getSelectedJdk() {
    String jdkName = getSelectedValue();
    Sdk jdk = null;
    try {
      jdk = ExternalSystemJdkUtil.getJdk(myProject, jdkName);
    }
    catch (ExternalSystemJdkException ignore) {
    }
    return jdk;
  }

  @NotNull
  public ExternalSystemJdkComboBox withoutJre() {
    suggestJre = false;
    return this;
  }

  public void refreshData(@Nullable String selectedValue) {
    refreshData(selectedValue, null);
  }

  public void refreshData(@Nullable String selectedValue, @Nullable Sdk projectJdk) {
    Map<String, JdkComboBoxItem> jdkMap = collectComboBoxItem(projectJdk);
    if (selectedValue != null && !jdkMap.containsKey(selectedValue)) {
      assert selectedValue.length() > 0;
      jdkMap.put(selectedValue, new JdkComboBoxItem(selectedValue, selectedValue, "", false));
    }

    removeAllItems();

    ComboBoxModel<JdkComboBoxItem> comboBoxModel = getModel();
    for (Map.Entry<String, JdkComboBoxItem> entry : jdkMap.entrySet()) {
      ((MutableComboBoxModel<JdkComboBoxItem>)comboBoxModel).addElement(entry.getValue());
    }

    select(comboBoxModel, selectedValue);
  }

  private static void select(ComboBoxModel<JdkComboBoxItem> model, Object value) {
    for (int i = 0; i < model.getSize(); i++) {
      JdkComboBoxItem item = model.getElementAt(i);
      if (item.jdkName.equals(value)) {
        model.setSelectedItem(item);
        return;
      }
    }
    if (model.getSize() != 0) {
      model.setSelectedItem(model.getElementAt(0));
    }
  }

  @Nullable
  public String getSelectedValue() {
    final DefaultComboBoxModel model = (DefaultComboBoxModel)getModel();
    final Object item = model.getSelectedItem();
    return item != null ? ((JdkComboBoxItem)item).jdkName : null;
  }

  private Map<String, JdkComboBoxItem> collectComboBoxItem(@Nullable Sdk projectSdk) {
    Map<String, JdkComboBoxItem> result = new LinkedHashMap<>();

    for (Sdk sdk : ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance())) {
      if (!((SdkType)sdk.getSdkType()).sdkHasValidPath(sdk)) {
        continue;
      }
      String name = sdk.getName();
      String comment = buildComment(sdk);
      result.put(name, new JdkComboBoxItem(name, name, comment, true));
    }

    if (suggestJre) {
      final Sdk internalJdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
      assert internalJdk.getHomePath() != null;
      result.put(ExternalSystemJdkUtil.USE_INTERNAL_JAVA,
                 new JdkComboBoxItem(
                   ExternalSystemJdkUtil.USE_INTERNAL_JAVA,
                   ExternalSystemBundle.message("external.system.java.internal.jre"),
                   buildComment(internalJdk),
                   true
                 ));
    }

    if (projectSdk == null) {
      if (myProject != null && !myProject.isDisposed()) {
        projectSdk = ProjectRootManager.getInstance(myProject).getProjectSdk();
      }
    }

    if (projectSdk != null) {
      result.put(ExternalSystemJdkUtil.USE_PROJECT_JDK,
                 new JdkComboBoxItem(ExternalSystemJdkUtil.USE_PROJECT_JDK, "Use Project JDK", buildComment(projectSdk), true));
    }

    String javaHomePath = EnvironmentUtil.getEnvironmentMap().get("JAVA_HOME");
    if (ExternalSystemJdkUtil.isValidJdk(javaHomePath)) {
      result.put(ExternalSystemJdkUtil.USE_JAVA_HOME,
                 new JdkComboBoxItem(
                   ExternalSystemJdkUtil.USE_JAVA_HOME, ExternalSystemBundle.message("external.system.java.home.env"),
                   truncateLongPath(javaHomePath), true
                 ));
    }
    return result;
  }

  private static String buildComment(@NotNull Sdk sdk) {
    String versionString = sdk.getVersionString();
    String path = sdk.getHomePath();
    StringBuilder buf = new StringBuilder();
    if (versionString != null) {
      buf.append(versionString);
    }
    if (path != null) {
      buf.append(versionString != null ? ", " : "");
      buf.append("path: ").append(truncateLongPath(path));
    }

    return buf.toString();
  }

  @NotNull
  private static String truncateLongPath(@NotNull String path) {
    if (path.length() > MAX_PATH_LENGTH) {
      return path.substring(0, MAX_PATH_LENGTH / 2) + "..." + path.substring(path.length() - MAX_PATH_LENGTH / 2 - 3);
    }

    return path;
  }

  private static SimpleTextAttributes getTextAttributes(final boolean valid, final boolean selected) {
    if (!valid) {
      return SimpleTextAttributes.ERROR_ATTRIBUTES;
    }
    else if (selected && !(SystemInfo.isWinVistaOrNewer && UIManager.getLookAndFeel().getName().contains("Windows"))) {
      return SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES;
    }
    else {
      return SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES;
    }
  }

  static class JdkComboBoxItem {
    private String jdkName;
    private String label;
    private String comment;
    private boolean valid;

    public JdkComboBoxItem(String jdkName, String label, String comment, boolean valid) {
      this.jdkName = jdkName;
      this.label = label;
      this.comment = comment;
      this.valid = valid;
    }
  }
}
