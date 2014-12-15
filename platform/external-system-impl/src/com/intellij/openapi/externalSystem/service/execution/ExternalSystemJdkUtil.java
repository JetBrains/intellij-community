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
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.callback.OpenExternalSystemSettingsCallback;
import com.intellij.openapi.externalSystem.service.notification.callback.OpenProjectJdkSettingsCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

public class ExternalSystemJdkUtil {

  @NonNls public static final String USE_INTERNAL_JAVA = "#JAVA_INTERNAL";
  @NonNls public static final String USE_PROJECT_JDK = "#USE_PROJECT_JDK";
  @NonNls public static final String USE_JAVA_HOME = "#JAVA_HOME";


  @Nullable
  public static Sdk getJdk(@Nullable Project project, @Nullable String jdkName) {
    if (jdkName == null) return null;

    if (USE_INTERNAL_JAVA.equals(jdkName)) {
      return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    }

    if (USE_PROJECT_JDK.equals(jdkName)) {
      if (project != null) {
        Sdk res = ProjectRootManager.getInstance(project).getProjectSdk();
        if (res != null) {
          return res;
        }
        Module[] modules = ModuleManager.getInstance(project).getModules();
        for (Module module : modules) {
          Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
          if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) {
            return sdk;
          }
        }
      }

      if (project == null) {
        Sdk recent = ProjectJdkTable.getInstance().findMostRecentSdkOfType(JavaSdk.getInstance());
        if (recent != null) return recent;
        return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
      }

      throw new ExternalSystemException(
        String.format("Project JDK is not specified. <a href='%s'>Configure</a>", OpenProjectJdkSettingsCallback.ID),
        OpenProjectJdkSettingsCallback.ID);
    }

    if (USE_JAVA_HOME.equals(jdkName)) {
      final String javaHome = System.getenv("JAVA_HOME");
      if (StringUtil.isEmptyOrSpaces(javaHome)) {
        throw new ExternalSystemException(ExternalSystemBundle.message("external.system.java.home.undefined"));
      }
      final Sdk jdk = JavaSdk.getInstance().createJdk("", javaHome);
      if (jdk == null) {
        throw new ExternalSystemException(ExternalSystemBundle.message("external.system.java.home.invalid", javaHome));
      }
      return jdk;
    }

    for (Sdk projectJdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (projectJdk.getName().equals(jdkName)) {
        return projectJdk;
      }
    }

    throw new ExternalSystemException(ExternalSystemBundle.message("external.system.java.home.invalid", jdkName));
  }


  private static class Item {
    private final Object value;
    private final String label;

    private Item(Object value, String label) {
      this.value = value;
      this.label = label;
    }

    public Object getValue() {
      return value;
    }

    public String toString() {
      return label;
    }
  }

  public static void addToModel(DefaultComboBoxModel model, Object value, String label) {
    model.addElement(new Item(value, label));
  }

  public static <T> void setModel(JComboBox comboBox, DefaultComboBoxModel model, Collection<T> values, Function<T, Pair<String, ?>> func) {
    model.removeAllElements();
    for (T each : values) {
      Pair<String, ?> pair = func.fun(each);
      addToModel(model, pair.second, pair.first);
    }
    comboBox.setModel(model);
  }

  public static void select(DefaultComboBoxModel model, Object value) {
    for (int i = 0; i < model.getSize(); i++) {
      Item comboBoxUtil = (Item)model.getElementAt(i);
      if (comboBoxUtil.getValue().equals(value)) {
        model.setSelectedItem(comboBoxUtil);
        return;
      }
    }
    if (model.getSize() != 0) {
      model.setSelectedItem(model.getElementAt(0));
    }
  }

  @Nullable
  public static String getSelectedString(DefaultComboBoxModel model) {
    return String.valueOf(getSelectedValue(model));
  }

  @Nullable
  public static Object getSelectedValue(DefaultComboBoxModel model) {
    final Object item = model.getSelectedItem();
    return item != null ? ((Item)item).getValue() : null;
  }
}
