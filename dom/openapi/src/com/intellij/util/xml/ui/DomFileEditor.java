/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.ui;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.UserActivityWatcher;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomEventAdapter;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

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
    final UserActivityWatcher userActivityWatcher = DomUIFactory.getDomUIFactory().createEditorAwareUserActivityWatcher();
    final CommitablePanelUserActivityListener userActivityListener = new CommitablePanelUserActivityListener() {
      protected void applyChanges() {
        commit();
      }
    };
    userActivityWatcher.addUserActivityListener(userActivityListener, this);
    userActivityWatcher.register(getComponent());
    new MnemonicHelper().register(getComponent());
    addWatchedElement(component.getDomElement());
    DomManager.getDomManager(project).addDomEventListener(new DomEventAdapter() {
      public void eventOccured(DomEvent event) {
        checkIsValid();
      }
    }, this);
    Disposer.register(this, myComponent);
    Disposer.register(this, userActivityListener);
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

  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return new BackgroundEditorHighlighter() {
      public HighlightingPass[] createPassesForEditor() {
        return ContainerUtil.map2Array(getDocuments(), HighlightingPass.class, new Function<Document, HighlightingPass>() {
          public HighlightingPass fun(final Document document) {
            return new TextEditorHighlightingPass(document) {
              public void doCollectInformation(ProgressIndicator progress) {
                final PsiFile file = getDocumentManager().getPsiFile(document);
                if (file instanceof XmlFile) {
                  final XmlFile xmlFile = (XmlFile)file;
                  final DomFileElement<DomElement> element = DomManager.getDomManager(getProject()).getFileElement(xmlFile);
                  if (element != null) {
                    DomElementAnnotationsManager.getInstance(getProject()).getProblemHolder(element);
                  }
                }
              }

              public void doApplyInformationToEditor() {
                reset();
              }

              public int getPassId() {
                return Pass.ALL;
              }
            };
          }
        });
      }

      public HighlightingPass[] createPassesForVisibleArea() {
        return createPassesForEditor();
      }
    };
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
