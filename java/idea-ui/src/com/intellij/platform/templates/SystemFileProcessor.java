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
package com.intellij.platform.templates;

import com.intellij.ide.util.projectWizard.ProjectTemplateComponent;
import com.intellij.ide.util.projectWizard.ProjectTemplateFileProcessor;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 2/15/13
 */
public class SystemFileProcessor extends ProjectTemplateFileProcessor {
  @Nullable
  @Override
  protected String encodeFileText(String content, VirtualFile file, Project project) throws IOException {
    final String fileName = file.getName();
    if (file.getParent().getName().equals(".idea") && fileName.equals("workspace.xml")) {
      ProjectTemplateComponent[] components = project.getComponents(ProjectTemplateComponent.class);
      List<ProjectTemplateComponent> componentList = ContainerUtil.filter(components, new Condition<ProjectTemplateComponent>() {
        @Override
        public boolean value(ProjectTemplateComponent component) {
          return fileName.equals(component.getStorageFile());
        }
      });
      if (!componentList.isEmpty()) {
        Element root = new Element("project");
        for (final ProjectTemplateComponent component : componentList) {
          final Element element = new Element("component");
          String name = ComponentManagerImpl.getComponentName(component);
          element.setAttribute("name", name);
          root.addContent(element);
          UIUtil.invokeAndWaitIfNeeded(new Runnable() {
            @Override
            public void run() {
              try {
                ((JDOMExternalizable)component).writeExternal(element);
              }
              catch (WriteExternalException ignore) {
                LOG.error(ignore);
              }
            }
          });
        }
        PathMacroManager.getInstance(project).collapsePaths(root);
        return JDOMUtil.writeElement(root);
      }
    }
    return null;
  }

  private static final Logger LOG = Logger.getInstance(SystemFileProcessor.class);
}
