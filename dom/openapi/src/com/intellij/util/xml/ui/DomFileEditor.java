/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.ui;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomEventAdapter;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.ui.UserActivityWatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import javax.swing.*;

/**
 * @author peter
 */
public class DomFileEditor<T extends BasicDomElementComponent> extends PerspectiveFileEditor{
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
  }

  public void commit() {
    if (checkIsValid()) {
      myComponent.commit();
    }
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return myComponent.getComponent();
  }

  protected final T getDomComponent() {
    return myComponent;
  }

  @NotNull
  protected JComponent createCustomComponent() {
    final UserActivityWatcher userActivityWatcher = DomUIFactory.getDomUIFactory().createEditorAwareUserActivityWatcher();
    final CommitablePanelUserActivityListener userActivityListener = new CommitablePanelUserActivityListener() {
      protected void applyChanges() {
        commit();
      }
    };
    userActivityWatcher.addUserActivityListener(userActivityListener, this);
    userActivityWatcher.register(getComponent());
    new MnemonicHelper().register(getComponent());
    myComponent = myComponentFactory.create();
    addWatchedElement(myComponent.getDomElement());
    DomManager.getDomManager(myComponent.getProject()).addDomEventListener(new DomEventAdapter() {
      public void eventOccured(DomEvent event) {
        checkIsValid();
      }
    }, this);
    Disposer.register(this, myComponent);
    Disposer.register(this, userActivityListener);
    return myComponent.getComponent();
  }

  @NotNull
  public final String getName() {
    return myName;
  }

  protected DomElement getSelectedDomElement() {
    final DomElement domElement = DomUINavigationProvider.findDomElement(myComponent);
    return domElement;
    //return domElement != null && domElement.getParent() instanceof DomFileElement ? null : domElement;
  }

  protected void setSelectedDomElement(DomElement domElement) {
    final DomUIControl domControl = DomUINavigationProvider.findDomControl(myComponent, domElement);
    if (domControl != null) {
      domControl.navigate(domElement);
    }
  }

  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    final DomManager domManager = DomManager.getDomManager(getProject());
    final DomElementAnnotationsManager annotationsManager = DomElementAnnotationsManager.getInstance(getProject());
    final WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(getProject());

    return new BackgroundEditorHighlighter() {
      public HighlightingPass[] createPassesForEditor() {
        return ContainerUtil.map2Array(getDocuments(), HighlightingPass.class, new Function<Document, HighlightingPass>() {
          public HighlightingPass fun(final Document document) {
            return new TextEditorHighlightingPass(getProject(), document) {
              public void doCollectInformation(ProgressIndicator progress) {
                final PsiFile file = getDocumentManager().getPsiFile(document);
                if (file instanceof XmlFile) {
                  final XmlFile xmlFile = (XmlFile)file;
                  final DomFileElement<DomElement> element = domManager.getFileElement(xmlFile);
                  if (element != null) {
                    annotationsManager.getProblemHolder(element);
                  }
                }
              }

              public void doApplyInformationToEditor() {
                final PsiFile file = getDocumentManager().getPsiFile(document);
                if (file instanceof XmlFile) {
                  final DomFileElement<DomElement> element = domManager.getFileElement((XmlFile)file);
                  if (!annotationsManager.getCachedProblemHolder(element).getProblems(element, true, true).isEmpty()) {
                    wolf.weHaveGotProblem(new Problem() {
                      public VirtualFile getVirtualFile() {
                        return file.getVirtualFile();
                      }
                    });
                  }
                }
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
    return super.isValid() && (!isInitialised() || myComponent.getDomElement().isValid());
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
