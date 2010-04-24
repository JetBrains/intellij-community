/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class LookupManagerImpl extends LookupManager implements ProjectComponent {
  private final Project myProject;

  protected LookupImpl myActiveLookup = null;
  protected Editor myActiveLookupEditor = null;
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

  private boolean myIsDisposed;

  public LookupManagerImpl(Project project, MessageBus bus) {
    myProject = project;

    bus.connect().subscribe(EditorHintListener.TOPIC, new EditorHintListener() {
      public void hintShown(final Project project, final LightweightHint hint, final int flags) {
        if (project == myProject) {
          Lookup lookup = getActiveLookup();
          if (lookup != null && (flags & HintManager.HIDE_BY_LOOKUP_ITEM_CHANGE) != 0) {
            lookup.addLookupListener(new LookupAdapter() {
              public void currentItemChanged(LookupEvent event) {
                hint.hide();
              }

              public void itemSelected(LookupEvent event) {
                hint.hide();
              }

              public void lookupCanceled(LookupEvent event) {
                hint.hide();
              }
            });
          }
        }
      }
    });

  }

  @NotNull
  public String getComponentName() {
    return "LookupManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {

    final EditorFactoryAdapter myEditorFactoryListener = new EditorFactoryAdapter() {
      public void editorReleased(EditorFactoryEvent event) {
        if (event.getEditor() == myActiveLookupEditor) {
          hideActiveLookup();
        }
      }
    };
    EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener);
    Disposer.register(myProject, new Disposable() {
      public void dispose() {
        EditorFactory.getInstance().removeEditorFactoryListener(myEditorFactoryListener);
      }
    });
  }

  public void projectClosed() {
    myIsDisposed = true;
  }

  public Lookup showLookup(final Editor editor,
                           @NotNull LookupElement[] items,
                           final String prefix,
                           @NotNull final LookupArranger arranger) {
    final LookupImpl lookup = createLookup(editor, items, prefix, arranger);
    lookup.show();
    return lookup;
  }

  public LookupImpl createLookup(final Editor editor,
                                 @NotNull LookupElement[] items,
                                 final String prefix,
                                 @NotNull final LookupArranger arranger) {
    hideActiveLookup();

    final CodeInsightSettings settings = CodeInsightSettings.getInstance();

    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());

    final Alarm alarm = new Alarm();
    final Runnable request = new Runnable() {
      public void run() {
        DocumentationManager.getInstance(myProject).showJavaDocInfo(editor, psiFile, false);
      }
    };
    if (settings.AUTO_POPUP_JAVADOC_INFO) {
      alarm.addRequest(request, settings.JAVADOC_INFO_DELAY);
    }

    final DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    if (daemonCodeAnalyzer != null) {
      daemonCodeAnalyzer.setUpdateByTimerEnabled(false);
    }
    myActiveLookup = new LookupImpl(myProject, editor, arranger);
    myActiveLookupEditor = editor;
    myActiveLookup.addLookupListener(new LookupAdapter() {
      public void itemSelected(LookupEvent event) {
        dispose();
      }

      public void lookupCanceled(LookupEvent event) {
        dispose();
      }

      public void currentItemChanged(LookupEvent event) {
        alarm.cancelAllRequests();
        if (settings.AUTO_POPUP_JAVADOC_INFO) {
          alarm.addRequest(request, settings.JAVADOC_INFO_DELAY);
        }
      }

      private void dispose() {
        alarm.cancelAllRequests();
        if (daemonCodeAnalyzer != null) {
          daemonCodeAnalyzer.setUpdateByTimerEnabled(true);
        }
        if (myActiveLookup == null) return;
        myActiveLookup.removeLookupListener(this);
        Lookup lookup = myActiveLookup;
        myActiveLookup = null;
        myActiveLookupEditor = null;
        myPropertyChangeSupport.firePropertyChange(PROP_ACTIVE_LOOKUP, lookup, null);
      }
    });

    if (items.length > 0) {
      for (final LookupElement item : items) {
        if (prefix != null) {
          item.setPrefixMatcher(new CamelHumpMatcher(prefix));
        }
        myActiveLookup.addItem(item);
      }
      myActiveLookup.refreshUi();
    } else {
      alarm.cancelAllRequests(); // no items -> no doc
    }

    myPropertyChangeSupport.firePropertyChange(PROP_ACTIVE_LOOKUP, null, myActiveLookup);
    return myActiveLookup;
  }

  public void hideActiveLookup() {
    if (myActiveLookup != null) {
      myActiveLookup.hide();
    }
  }

  public Lookup getActiveLookup() {
    return myActiveLookup;
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  public boolean isDisposed() {
    return myIsDisposed;
  }

}
