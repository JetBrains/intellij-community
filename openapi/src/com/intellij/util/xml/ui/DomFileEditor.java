/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.UserActivityListener;
import com.intellij.ui.UserActivityWatcher;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.CaptionComponent;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * @author peter
 */
public abstract class DomFileEditor<T extends BasicDomElementComponent> extends PerspectiveFileEditor{
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private final String myName;
  private final T myComponent;
  private final UserActivityWatcher myUserActivityWatcher;
  private final UserActivityListener myUserActivityListener;

  protected DomFileEditor(final Project project, final VirtualFile file, final String name, final T component) {
    super(project, file);
    myComponent = component;
    myName = name;
    myUserActivityWatcher = DomUIFactory.getDomUIFactory().createEditorAwareUserActivityWatcher();
    myUserActivityListener = new CommitablePanelUserActivityListener() {
      protected void applyChanges() {
        doCommit();
      }
    };
    myUserActivityWatcher.addUserActivityListener(myUserActivityListener);
    myUserActivityWatcher.register(getComponent());
    new MnemonicHelper().register(getComponent());
    addWatchedElement(component.getDomElement());
  }

  public void dispose() {
    myUserActivityWatcher.removeUserActivityListener(myUserActivityListener);
    super.dispose();
  }

  public void commit() {
    if (checkIsValid()) {
      myComponent.commit();
    }
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

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  protected final boolean checkIsValid() {
    if (!myComponent.getDomElement().isValid()) {
      myPropertyChangeSupport.firePropertyChange(FileEditor.PROP_VALID, Boolean.TRUE, Boolean.FALSE);
      return false;
    }
    return true;
  }

  public void reset() {
    if (checkIsValid()) {
      myComponent.reset();
    }
  }

  public static DomFileEditor createDomFileEditor(final String name,
                                                          final DomElement element,
                                                          final CaptionComponent captionComponent,
                                                          final CommittablePanel committablePanel) {

    final XmlFile file = element.getRoot().getFile();
    return new DomFileEditor(file.getProject(), file.getVirtualFile(), name,
                             createComponentWithCaption(committablePanel, captionComponent, element)) {
      public JComponent getPreferredFocusedComponent() {
        return null;
      }
    };
  }

  public static BasicDomElementComponent createComponentWithCaption(final CommittablePanel committablePanel,
                                                                     final CaptionComponent captionComponent,
                                                                     final DomElement element) {
    final JComponent component1 = committablePanel.getComponent();
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(captionComponent, BorderLayout.NORTH);
    panel.add(component1, BorderLayout.CENTER);

    BasicDomElementComponent component = new BasicDomElementComponent(element) {
      public JComponent getComponent() {
        return panel;
      }
    };

    component.addComponent(committablePanel);
    component.addComponent(captionComponent);
    return component;
  }
}
