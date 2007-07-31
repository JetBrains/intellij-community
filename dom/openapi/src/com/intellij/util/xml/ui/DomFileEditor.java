/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.ui;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomEventAdapter;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.events.DomEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author peter
 */
public class DomFileEditor<T extends BasicDomElementComponent> extends PerspectiveFileEditor implements CommittablePanel, Highlightable {
  private final String myName;
  private final Factory<? extends T> myComponentFactory;
  private T myComponent;

  public DomFileEditor(final DomElement element, final String name, final T component) {
    this(element.getManager().getProject(), element.getRoot().getFile().getVirtualFile(), name, component);
  }

  public DomFileEditor(final Project project, final VirtualFile file, final String name, final T component) {
    this(project, file, name, new Factory<T>() {
      public T create() {
        return component;
      }
    });
  }

  public DomFileEditor(final Project project, final VirtualFile file, final String name, final Factory<? extends T> component) {
    super(project, file);
    myComponentFactory = component;
    myName = name;
    
    DomElementAnnotationsManager.getInstance(project).addHighlightingListener(new DomElementAnnotationsManager.DomHighlightingListener() {
      public void highlightingFinished(@NotNull DomFileElement element) {
        if (isInitialised() && getComponent().isShowing() && element.isValid()) {
          updateHighlighting();
        }
      }
    }, this);
  }

  public void updateHighlighting() {
    if (checkIsValid()) {
      CommittableUtil.updateHighlighting(myComponent);
    }
  }

  public void commit() {
    if (checkIsValid()) {
      setShowing(false);
      try {
        ServiceManager.getService(getProject(), CommittableUtil.class).commit(myComponent);
      }
      finally {
        setShowing(true);
      }
    }
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    ensureInitialized();
    return myComponent.getComponent();
  }

  protected final T getDomComponent() {
    return myComponent;
  }

  @NotNull
  protected JComponent createCustomComponent() {
    new MnemonicHelper().register(getComponent());
    myComponent = myComponentFactory.create();
    DomUIFactory.getDomUIFactory().setupErrorOutdatingUserActivityWatcher(this, getDomElement());
    DomManager.getDomManager(getProject()).addDomEventListener(new DomEventAdapter() {
      public void eventOccured(DomEvent event) {
        checkIsValid();
      }
    }, this);
    Disposer.register(this, myComponent);
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

  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    ensureInitialized();
    return DomUIFactory.getDomUIFactory().createDomHighlighter(getProject(), this, getDomElement());
  }

  private DomElement getDomElement() {
    return myComponent.getDomElement();
  }


  public boolean isValid() {
    return super.isValid() && (!isInitialised() || getDomElement().isValid());
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
    final DomFileEditor editor = createDomFileEditor(name, element, captionComponent, new Factory<CommittablePanel>() {
      public CommittablePanel create() {
        return committablePanel;
      }
    });
    Disposer.register(editor, committablePanel);
    return editor;
  }

  public static DomFileEditor createDomFileEditor(final String name,
                                                  final DomElement element,
                                                  final CaptionComponent captionComponent,
                                                  final Factory<? extends CommittablePanel> committablePanel) {

    final XmlFile file = element.getRoot().getFile();
    final Factory<BasicDomElementComponent> factory = new Factory<BasicDomElementComponent>() {
      public BasicDomElementComponent create() {
        return createComponentWithCaption(committablePanel.create(), captionComponent, element);
      }
    };
    final DomFileEditor<BasicDomElementComponent> editor =
      new DomFileEditor<BasicDomElementComponent>(file.getProject(), file.getVirtualFile(), name, factory) {
        public JComponent getPreferredFocusedComponent() {
          return null;
        }
      };
    Disposer.register(editor, captionComponent);
    return editor;
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
