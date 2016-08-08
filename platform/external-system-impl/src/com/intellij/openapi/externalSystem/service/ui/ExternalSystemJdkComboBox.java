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

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ColoredListCellRendererWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class ExternalSystemJdkComboBox extends ComboBoxWithWidePopup {

  private static final int MAX_PATH_LENGTH = 50;

  @Nullable
  private Project myProject;
  private boolean suggestJre = true;

  public ExternalSystemJdkComboBox() {
    this(null);
  }

  public ExternalSystemJdkComboBox(@Nullable Project project) {
    myProject = project;
    setRenderer(new ColoredListCellRendererWrapper() {

      @Override
      protected void doCustomize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        JdkComboBoxItem item = (JdkComboBoxItem)value;
        CompositeAppearance appearance = new CompositeAppearance();
        SdkType sdkType = JavaSdk.getInstance();
        appearance.setIcon(sdkType.getIcon());
        SimpleTextAttributes attributes = getTextAttributes(item.valid, selected);
        CompositeAppearance.DequeEnd ending = appearance.getEnding();

        ending.addText(item.label, attributes);
        if (item.comment != null && !item.comment.equals(item.jdkName)) {
          final SimpleTextAttributes textAttributes;
          if (!item.valid) {
            textAttributes = SimpleTextAttributes.ERROR_ATTRIBUTES;
          }
          else {
            textAttributes = SystemInfo.isMac && selected
                             ? new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.WHITE)
                             : SimpleTextAttributes.GRAY_ATTRIBUTES;
          }

          ending.addComment(item.comment, textAttributes);
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

  @NotNull
  public ExternalSystemJdkComboBox withoutJre() {
    suggestJre = false;
    return this;
  }

  public void refreshData(@Nullable String selectedValue) {
    Map<String, JdkComboBoxItem> jdkMap = collectComboBoxItem();
    if (selectedValue != null && !jdkMap.containsKey(selectedValue)) {
      assert selectedValue.length() > 0;
      jdkMap.put(selectedValue, new JdkComboBoxItem(selectedValue, selectedValue, "", false));
    }

    removeAllItems();

    for (Map.Entry<String, JdkComboBoxItem> entry : jdkMap.entrySet()) {
      //noinspection unchecked
      ((DefaultComboBoxModel)getModel()).addElement(entry.getValue());
    }

    select((DefaultComboBoxModel)getModel(), selectedValue);
  }

  private static void select(DefaultComboBoxModel model, Object value) {
    for (int i = 0; i < model.getSize(); i++) {
      JdkComboBoxItem comboBoxUtil = (JdkComboBoxItem)model.getElementAt(i);
      if (comboBoxUtil.jdkName.equals(value)) {
        model.setSelectedItem(comboBoxUtil);
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

  private Map<String, JdkComboBoxItem> collectComboBoxItem() {
    Map<String, JdkComboBoxItem> result = new LinkedHashMap<>();

    for (Sdk projectJdk : ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance())) {
      String name = projectJdk.getName();
      String comment = buildComment(projectJdk);
      result.put(name, new JdkComboBoxItem(name, name, comment, ((SdkType)projectJdk.getSdkType()).sdkHasValidPath(projectJdk)));
    }

    if(suggestJre) {
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

    if (myProject != null && !myProject.isDisposed()) {
      final Sdk projectSdk = ProjectRootManager.getInstance(myProject).getProjectSdk();
      result.put(ExternalSystemJdkUtil.USE_PROJECT_JDK,
                 new JdkComboBoxItem(
                   ExternalSystemJdkUtil.USE_PROJECT_JDK, "Use Project JDK",
                   projectSdk == null ? "not defined yet" : buildComment(projectSdk), projectSdk != null
                 ));
    }

    String javaHomePath = System.getenv("JAVA_HOME");
    String javaHomeLabel = ExternalSystemBundle.message("external.system.java.home.env");

    result.put(ExternalSystemJdkUtil.USE_JAVA_HOME,
               new JdkComboBoxItem(
                 ExternalSystemJdkUtil.USE_JAVA_HOME, javaHomeLabel,
                 javaHomePath == null ? "not defined yet" : truncateLongPath(javaHomePath), javaHomePath != null
               ));

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

  private static class JdkComboBoxItem {
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
