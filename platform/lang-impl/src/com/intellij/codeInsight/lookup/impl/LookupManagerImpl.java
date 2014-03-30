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
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBus;
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
      @Override
      public void hintShown(final Project project, final LightweightHint hint, final int flags) {
        if (project == myProject) {
          Lookup lookup = getActiveLookup();
          if (lookup != null && (flags & HintManager.HIDE_BY_LOOKUP_ITEM_CHANGE) != 0) {
            lookup.addLookupListener(new LookupAdapter() {
              @Override
              public void currentItemChanged(LookupEvent event) {
                hint.hide();
              }

              @Override
              public void itemSelected(LookupEvent event) {
                hint.hide();
              }

              @Override
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
      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        if (event.getEditor() == myActiveLookupEditor) {
          hideActiveLookup();
        }
      }
    };
    EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener, myProject);
  }

  @Override
  public LookupEx showLookup(@NotNull final Editor editor,
                           @NotNull LookupElement[] items,
                           @NotNull final String prefix,
                           @NotNull final LookupArranger arranger) {
    for (LookupElement item : items) {
      assert item != null;
    }

    LookupImpl lookup = createLookup(editor, items, prefix, arranger);
    return lookup.showLookup() ? lookup : null;
  }

  @NotNull
  @Override
  public LookupImpl createLookup(@NotNull final Editor editor,
                                 @NotNull LookupElement[] items,
                                 @NotNull final String prefix,
                                 @NotNull final LookupArranger arranger) {
    hideActiveLookup();

    final CodeInsightSettings settings = CodeInsightSettings.getInstance();

    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());

    final LookupImpl lookup = new LookupImpl(myProject, editor, arranger);
    DaemonCodeAnalyzer.getInstance(myProject).disableUpdateByTimer(lookup);

    final Alarm alarm = new Alarm();
    final Runnable request = new Runnable() {
      @Override
      public void run() {
        if (myActiveLookup != lookup) return;
        
        LookupElement currentItem = lookup.getCurrentItem();
        if (currentItem != null && currentItem.isValid()) {
          final CompletionProcess completion = CompletionService.getCompletionService().getCurrentCompletion();
          if (completion != null && !completion.isAutopopupCompletion()) {
            try {
              DocumentationManager.getInstance(myProject).showJavaDocInfo(editor, psiFile, false);
            }
            catch (IndexNotReadyException ignored) {
            }
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
      @Override
      public void itemSelected(LookupEvent event) {
        lookupClosed();
      }

      @Override
      public void lookupCanceled(LookupEvent event) {
        lookupClosed();
      }

      @Override
      public void currentItemChanged(LookupEvent event) {
        alarm.cancelAllRequests();
        if (settings.AUTO_POPUP_JAVADOC_INFO) {
          alarm.addRequest(request, settings.JAVADOC_INFO_DELAY);
        }
      }

      private void lookupClosed() {
        ApplicationManager.getApplication().assertIsDispatchThread();

        alarm.cancelAllRequests();
        LookupImpl lookup = myActiveLookup;
        if (lookup == null) return;

        LOG.assertTrue(lookup.isLookupDisposed());
        myActiveLookup = null;
        myActiveLookupEditor = null;
        lookup.removeLookupListener(this);
        myPropertyChangeSupport.firePropertyChange(PROP_ACTIVE_LOOKUP, lookup, null);
      }
    });

    CamelHumpMatcher matcher = new CamelHumpMatcher(prefix);
    if (items.length > 0) {
      for (final LookupElement item : items) {
        myActiveLookup.addItem(item, matcher);
      }
      myActiveLookup.refreshUi(true, true);
    }
    else {
      alarm.cancelAllRequests(); // no items -> no doc
    }

    myPropertyChangeSupport.firePropertyChange(PROP_ACTIVE_LOOKUP, null, myActiveLookup);
    return lookup;
  }

  @Override
  public void hideActiveLookup() {
    LookupImpl lookup = myActiveLookup;
    if (lookup != null) {
      lookup.checkValid();
      lookup.hide();
      LOG.assertTrue(lookup.isLookupDisposed(), "Should be disposed");
    }
  }

  @Override
  public LookupEx getActiveLookup() {
    if (myActiveLookup != null && myActiveLookup.isLookupDisposed()) {
      LookupImpl lookup = myActiveLookup;
      myActiveLookup = null;
      lookup.checkValid();
    }

    return myActiveLookup;
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  @Override
  public void addPropertyChangeListener(@NotNull final PropertyChangeListener listener, @NotNull Disposable disposable) {
    addPropertyChangeListener(listener);
    Disposer.register(disposable, new Disposable() {
      @Override
      public void dispose() {
        removePropertyChangeListener(listener);
      }
    });
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
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
