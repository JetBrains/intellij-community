/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.platform.templates;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * @author Dmitry Avdeev
 *         Date: 11/14/12
 */
public class RemoteTemplatesFactory implements ProjectTemplatesFactory {

  @NotNull
  @Override
  public String[] getGroups() {
    return new String[] { "Samples Gallery"};
  }

  @NotNull
  @Override
  public ProjectTemplate[] createTemplates(String group, WizardContext context) {
    try {
      return createFromText("");
    }
    catch (Exception e) {
      return ProjectTemplate.EMPTY_ARRAY;
    }
  }

  public static ProjectTemplate[] createFromText(String text) throws IOException, JDOMException {
    @SuppressWarnings("unchecked")
    List<Element> templates = JDOMUtil.loadDocument(text).getRootElement().getChildren("template");
    return ContainerUtil.map2Array(templates, ProjectTemplate.class, new Function<Element, ProjectTemplate>() {
      @Override
      public ProjectTemplate fun(final Element element) {

        String type = element.getChildText("moduleType");
        final ModuleType moduleType = ModuleTypeManager.getInstance().findByID(type);
        return new ArchivedProjectTemplate(element.getChildTextTrim("name")) {
          @Override
          protected ModuleType getModuleType() {
            return moduleType;
          }

          @Override
          public ZipInputStream getStream() throws IOException {
            return null;
          }

          @Nullable
          @Override
          public String getDescription() {
            return element.getChildTextTrim("description");
          }
        };
      }
    });
  }
}
