// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.templates;

import com.intellij.configurationStore.JbXmlOutputter;
import com.intellij.ide.util.projectWizard.ProjectTemplateFileProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.NamedComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.configurationStore.StoreUtilKt.getStateSpec;

final class SystemFileProcessor extends ProjectTemplateFileProcessor {
  private static final String[] COMPONENT_NAMES = new String[] {
    FileEditorManager.class.getName(),
    "org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent"
  };

  @Nullable
  @Override
  protected String encodeFileText(String content, VirtualFile file, Project project) throws IOException {
    String fileName = file.getName();
    if (!file.getParent().getName().equals(Project.DIRECTORY_STORE_FOLDER) || !fileName.equals("workspace.xml")) {
      return null;
    }

    List<Object> componentList = new ArrayList<>();
    for (String componentName : COMPONENT_NAMES) {
      Object component = componentName.equals("org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent") ? project.getPicoContainer().getComponentInstance(componentName) : project.getComponent(componentName);
      if (component == null) {
        try {
          Class<?> aClass = Class.forName(componentName);
          component = project.getComponent(aClass);
          if (component == null) {
            component = project.getService(aClass);
          }
        }
        catch (ClassNotFoundException ignore) {
        }
      }
      ContainerUtil.addIfNotNull(componentList, component);
    }

    if (componentList.isEmpty()) {
      return null;
    }

    Element root = new Element("project");
    for (Object component : componentList) {
      final Element element = new Element("component");
      element.setAttribute("name", component instanceof NamedComponent ? ((NamedComponent)component).getComponentName() : component.getClass().getName());
      root.addContent(element);
      ApplicationManager.getApplication().invokeAndWait(() -> {
        if (component instanceof JDOMExternalizable) {
          try {
            ((JDOMExternalizable)component).writeExternal(element);
          }
          catch (WriteExternalException ignore) {
            LOG.error(ignore);
          }
        }
        else if (component instanceof PersistentStateComponent) {
          Object state = WriteAction.compute(() -> ((PersistentStateComponent)component).getState());

          if(state == null){
            return;
          }
          Element element1 = state instanceof Element ? (Element)state : XmlSerializer.serialize(state);
          element.addContent(element1.cloneContent());
          element.setAttribute("name", getStateSpec((PersistentStateComponent)component).name());
        }
      }, ModalityState.defaultModalityState());
    }
    return JbXmlOutputter.collapseMacrosAndWrite(root, project);
  }

  private static final Logger LOG = Logger.getInstance(SystemFileProcessor.class);
}
