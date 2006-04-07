/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.DomElement;

import javax.swing.*;

/**
 * @author peter
 */
public abstract class DomFileEditor<T extends BasicDomElementComponent> extends PerspectiveFileEditor{
  private final String myName;
  private final T myComponent;

  protected DomFileEditor(final Project project, final VirtualFile file, final String name, final T component) {
    super(project, file);
    myComponent = component;
    myName = name;
  }

  public void commit() {
    myComponent.commit();
  }

  protected final T getDomComponent() {
    return myComponent;
  }

  public JComponent getComponent() {
    return myComponent.getComponent();
  }

  public final String getName() {
    return myName;
  }

  protected DomElement getSelectedDomElement() {
    return DomUINavigationProvider.findDomElement(myComponent);
  }

  protected void setSelectedDomElement(DomElement domElement) {
    final DomUIControl domControl = DomUINavigationProvider.findDomControl(myComponent, domElement);
    if (domControl != null) {
      domControl.navigate(domElement);
    }
  }

  public void reset() {
    myComponent.reset();
  }
}
