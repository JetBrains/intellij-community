/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.util.*;
import com.intellij.openapi.project.Project;
import org.jdom.Element;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 22, 2004
 */
public class ModuleEditorState implements ProjectComponent, JDOMExternalizable{

  public String LAST_EDITED_MODULE_NAME;
  public String LAST_EDITED_TAB_NAME;

  public static ModuleEditorState getInstance(Project project) {
    return (ModuleEditorState)project.getComponent(ModuleEditorState.class);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getComponentName() {
    return "ModuleEditorState";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }
}
