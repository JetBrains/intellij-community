/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.util.projectWizard.ProjectTemplateFileProcessor;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.components.impl.stores.StoreUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 2/15/13
 */
public class SystemFileProcessor extends ProjectTemplateFileProcessor {

  private static final String[] COMPONENT_NAMES = new String[] {
    FileEditorManager.class.getName(),
    "org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent"
  };

  @Nullable
  @Override
  protected String encodeFileText(String content, VirtualFile file, Project project) throws IOException {
    final String fileName = file.getName();
    if (file.getParent().getName().equals(Project.DIRECTORY_STORE_FOLDER) && fileName.equals("workspace.xml")) {

      List<Object> componentList = new ArrayList<>();
      for (String componentName : COMPONENT_NAMES) {
        Object component = project.getComponent(componentName);
        if (component == null) {
          try {
            Class<?> aClass = Class.forName(componentName);
            component = project.getComponent(aClass);
            if(component == null) {
              component = ServiceManager.getService(project, aClass);
            }
          }
          catch (ClassNotFoundException ignore) {
          }
        }
        ContainerUtil.addIfNotNull(componentList, component);
      }
      if (!componentList.isEmpty()) {
        final Element root = new Element("project");
        for (final Object component : componentList) {
          final Element element = new Element("component");
          element.setAttribute("name", ComponentManagerImpl.getComponentName(component));
          root.addContent(element);
          UIUtil.invokeAndWaitIfNeeded(new Runnable() {
            @Override
            public void run() {
              if (component instanceof JDOMExternalizable) {
                try {
                  ((JDOMExternalizable)component).writeExternal(element);
                }
                catch (WriteExternalException ignore) {
                  LOG.error(ignore);
                }
              }
              else if (component instanceof PersistentStateComponent) {
                Object state = ((PersistentStateComponent)component).getState();
                if(state == null){
                  return;
                }
                Element element1 = state instanceof Element ? (Element)state : XmlSerializer.serialize(state);
                element.addContent(element1.cloneContent());
                element.setAttribute("name", StoreUtil.getStateSpec((PersistentStateComponent)component).name());
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
