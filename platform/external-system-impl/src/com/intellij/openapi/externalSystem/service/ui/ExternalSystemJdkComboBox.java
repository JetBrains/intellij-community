package com.intellij.openapi.externalSystem.service.ui;

import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ProjectRootManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.intellij.openapi.externalSystem.service.ui.ComboBoxUtil.*;

/**
 * @author Sergey Evdokimov
 */
public class ExternalSystemJdkComboBox extends JComboBox {

  private static final int MAX_PATH_LENGTH = 50;

  @Nullable
  private final Project myProject;

  public ExternalSystemJdkComboBox(@Nullable Project project) {
    myProject = project;
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  public void refreshData(@Nullable String selectedValue) {
    Map<String, String> jdkMap = collectJdkNamesAndDescriptions();
    if (selectedValue != null && !jdkMap.containsKey(selectedValue)) {
      assert selectedValue.length() > 0;
      jdkMap.put(selectedValue, selectedValue);
    }

    removeAllItems();

    for (Map.Entry<String, String> entry : jdkMap.entrySet()) {
      addToModel((DefaultComboBoxModel)getModel(), entry.getKey(), entry.getValue());
    }

    select((DefaultComboBoxModel)getModel(), selectedValue);
  }

  public String getSelectedValue() {
    return getSelectedString((DefaultComboBoxModel)getModel());
  }

  private Map<String, String> collectJdkNamesAndDescriptions() {
    Map<String, String> result = new LinkedHashMap<String, String>();

    for (Sdk projectJdk : ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance())) {
      String name = projectJdk.getName();

      String label;

      String path = projectJdk.getHomePath();
      if (path == null) {
        label = name;
      }
      else {
        label = String.format("<html>%s <font color=gray>(%s)</font></html>", name, truncateLongPath(path));
      }

      result.put(name, label);
    }

    String internalJdkPath = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk().getHomePath();
    assert internalJdkPath != null;
    result.put(USE_INTERNAL_JAVA, ExternalSystemBundle.message("maven.java.internal", truncateLongPath(internalJdkPath)));

    if (myProject != null) {
      String projectJdk = ProjectRootManager.getInstance(myProject).getProjectSdkName();
      String projectJdkTitle = String.format("<html>Use Project JDK <font color=gray>(%s)</font></html>", projectJdk == null ? "not defined yet" : projectJdk);
      result.put(USE_PROJECT_JDK, projectJdkTitle);
    }

    String javaHomePath = System.getenv("JAVA_HOME");
    String javaHomeLabel = ExternalSystemBundle.message("maven.java.home.env", javaHomePath == null ? "not defined yet" : truncateLongPath(javaHomePath));

    result.put(USE_JAVA_HOME, javaHomeLabel);

    return result;
  }

  @NotNull
  private static String truncateLongPath(@NotNull String path) {
    if (path.length() > MAX_PATH_LENGTH) {
      return path.substring(0, MAX_PATH_LENGTH / 2) + "..." + path.substring(path.length() - MAX_PATH_LENGTH / 2 - 3);
    }

    return path;
  }

}
