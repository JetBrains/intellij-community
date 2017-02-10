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
package com.intellij.openapi.module;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.ProjectTemplateParameterFactory;
import com.intellij.ide.util.projectWizard.WizardInputField;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.PsiNameHelperImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 *         Date: 2/1/13
 */
public class BasePackageParameterFactory extends ProjectTemplateParameterFactory {

  private static final Condition<PsiPackage> PACKAGE_CONDITION =
    aPackage -> PsiNameHelper.getInstance(aPackage.getProject()).isQualifiedName(aPackage.getQualifiedName()) &&
              Character.isLowerCase(aPackage.getName().charAt(0));

  @Override
  public String getParameterId() {
    return IJ_BASE_PACKAGE;
  }

  @Override
  public WizardInputField createField(final String defaultValue) {

    return new WizardInputField<JTextField>(IJ_BASE_PACKAGE, defaultValue) {

      private final JTextField myField = new JTextField(PropertiesComponent.getInstance().getValue(IJ_BASE_PACKAGE, defaultValue));

      @Override
      public String getLabel() {
        return "Base \u001Bpackage:";
      }

      @Override
      public JTextField getComponent() {
        return myField;
      }

      @Override
      public String getValue() {
        return myField.getText();
      }

      @Override
      public Map<String, String> getValues() {
        HashMap<String, String> map = new HashMap<>(2);
        map.put(getId(), getValue());
        map.put("IJ_BASE_PACKAGE_DIR", getValue().replace('.', '/'));
        map.put("IJ_BASE_PACKAGE_PREFIX", StringUtil.isEmpty(getValue()) ? "" : getValue() + ".");
        return map;
      }

      @Override
      public boolean validate() throws ConfigurationException {
        String value = getValue();
        if (!StringUtil.isEmpty(value) && !PsiNameHelperImpl.getInstance().isQualifiedName(value)) {
          throw new ConfigurationException(value + " is not a valid package name");
        }
        PropertiesComponent.getInstance().setValue(IJ_BASE_PACKAGE, value);
        return true;
      }
    };
  }

  @Override
  public String detectParameterValue(Project project) {
    PsiPackage root = JavaPsiFacade.getInstance(project).findPackage("");
    if (root == null) return null;
    String name = getBasePackage(root, GlobalSearchScope.projectScope(project)).getQualifiedName();
    return StringUtil.isEmpty(name) ? null : name;
  }

  private static PsiPackage getBasePackage(PsiPackage pack, GlobalSearchScope scope) {
    List<PsiPackage> subPackages = ContainerUtil.filter(pack.getSubPackages(scope), PACKAGE_CONDITION);
    return subPackages.size() == 1 ? getBasePackage(subPackages.get(0), scope) : pack;
  }
}
