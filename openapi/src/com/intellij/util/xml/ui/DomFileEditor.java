/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.UserActivityListener;
import com.intellij.ui.UserActivityWatcher;
import com.intellij.util.xml.CaptionComponent;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomEventListener;
import com.intellij.util.xml.events.DomEvent;

import javax.swing.*;
import java.awt.*;

import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class DomFileEditor<T extends BasicDomElementComponent> extends PerspectiveFileEditor{
  private final String myName;
  private final T myComponent;
  private final UserActivityWatcher myUserActivityWatcher;
  private final UserActivityListener myUserActivityListener;
  private DomEventListener myDomListener;

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
    myDomListener = new DomEventListener() {
      public void eventOccured(DomEvent event) {
        checkIsValid();
      }
    };
    DomManager.getDomManager(project).addDomEventListener(myDomListener);
  }

  public void dispose() {
    DomManager.getDomManager(getProject()).removeDomEventListener(myDomListener);
    myUserActivityWatcher.removeUserActivityListener(myUserActivityListener);
    myComponent.dispose();
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

  @NotNull
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

  public boolean isValid() {
    return super.isValid() && myComponent.getDomElement().isValid();
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
