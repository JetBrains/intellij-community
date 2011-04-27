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
import com.intellij.codeInsight.completion.CompletionProcess;
import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class LookupManagerImpl extends LookupManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.lookup.impl.LookupManagerImpl");
  private final Project myProject;
  private LookupImpl myActiveLookup = null;
  private Editor myActiveLookupEditor = null;
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

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

    bus.connect().subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        hideActiveLookup();
      }

      @Override
      public void exitDumbMode() {
        hideActiveLookup();
      }
    });


    final EditorFactoryAdapter myEditorFactoryListener = new EditorFactoryAdapter() {
      public void editorReleased(EditorFactoryEvent event) {
        if (event.getEditor() == myActiveLookupEditor) {
          hideActiveLookup();
        }
      }
    };
    EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener, myProject);
  }

  public LookupEx showLookup(final Editor editor,
                           @NotNull LookupElement[] items,
                           final String prefix,
                           @NotNull final LookupArranger arranger) {
    for (LookupElement item : items) {
      assert item != null;
    }

    final LookupImpl lookup = createLookup(editor, items, prefix, arranger);
    lookup.show();
    if (CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP) {
      lookup.setStartCompletionWhenNothingMatches(true);
    }
    return lookup;
  }

  public LookupImpl createLookup(final Editor editor,
                                 @NotNull LookupElement[] items,
                                 final String prefix,
                                 @NotNull final LookupArranger arranger) {
    hideActiveLookup();

    final CodeInsightSettings settings = CodeInsightSettings.getInstance();

    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());

    final DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    final boolean previousUpdate;
    if (daemonCodeAnalyzer != null) {
      previousUpdate = ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).isUpdateByTimerEnabled();
      daemonCodeAnalyzer.setUpdateByTimerEnabled(false);
    }
    else {
      previousUpdate = false;
    }
    final LookupImpl lookup = new LookupImpl(myProject, editor, arranger);

   final UiNotifyConnector connector = new UiNotifyConnector(editor.getContentComponent(), new Activatable() {
      @Override
      public void showNotify() {
      }

      @Override
      public void hideNotify() {
        hideActiveLookup();
      }
    });

    final Alarm alarm = new Alarm();
    final Runnable request = new Runnable() {
      public void run() {
        if (myActiveLookup == lookup && lookup.getCurrentItem() != null) {
          final CompletionProcess completion = CompletionService.getCompletionService().getCurrentCompletion();
          if (completion == null || !completion.isAutopopupCompletion()) {
            DocumentationManager.getInstance(myProject).showJavaDocInfo(editor, psiFile, false);
          }
        }
      }
    };
    if (settings.AUTO_POPUP_JAVADOC_INFO) {
      alarm.addRequest(request, settings.JAVADOC_INFO_DELAY);
    }

    ApplicationManager.getApplication().assertIsDispatchThread();

    myActiveLookup = lookup;
    myActiveLookupEditor = editor;
    myActiveLookup.addLookupListener(new LookupAdapter() {
      public void itemSelected(LookupEvent event) {
        lookupClosed();
      }

      public void lookupCanceled(LookupEvent event) {
        lookupClosed();
      }

      public void currentItemChanged(LookupEvent event) {
        alarm.cancelAllRequests();
        if (settings.AUTO_POPUP_JAVADOC_INFO) {
          alarm.addRequest(request, settings.JAVADOC_INFO_DELAY);
        }
      }

      private void lookupClosed() {
        alarm.cancelAllRequests();
        if (daemonCodeAnalyzer != null) {
          daemonCodeAnalyzer.setUpdateByTimerEnabled(previousUpdate);
        }
        if (myActiveLookup == null) return;
        LOG.assertTrue(myActiveLookup.isLookupDisposed());
        myActiveLookup.removeLookupListener(this);
        Disposer.dispose(connector);
        Lookup lookup = myActiveLookup;
        ApplicationManager.getApplication().assertIsDispatchThread();
        myActiveLookup = null;
        myActiveLookupEditor = null;
        myPropertyChangeSupport.firePropertyChange(PROP_ACTIVE_LOOKUP, lookup, null);
      }
    });

    CamelHumpMatcher matcher = new CamelHumpMatcher(prefix == null ? "" : prefix);
    if (items.length > 0) {
      for (final LookupElement item : items) {
        myActiveLookup.addItem(item, matcher);
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

  public LookupEx getActiveLookup() {
    return myActiveLookup;
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }


  @TestOnly
  public void forceSelection(char completion, int index){
    if(myActiveLookup == null) throw new RuntimeException("There are no items in this lookup");
    final LookupElement lookupItem = myActiveLookup.getItems().get(index);
    myActiveLookup.setCurrentItem(lookupItem);
    myActiveLookup.finishLookup(completion);
  }

  @TestOnly
  public void forceSelection(char completion, LookupElement item){
    myActiveLookup.setCurrentItem(item);
    myActiveLookup.finishLookup(completion);
  }

  @TestOnly
  public void clearLookup() {
    if (myActiveLookup != null) {
      myActiveLookup.hide();
      myActiveLookup = null;
    }
  }
}
