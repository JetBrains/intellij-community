/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Expirable;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.EventObject;

/**
 * @author peter
 */
public abstract class CompletionPhase implements Disposable {
  public static final CompletionPhase NoCompletion = new CompletionPhase(null) {
    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      return time;
    }
  };

  public final CompletionProgressIndicator indicator;

  protected CompletionPhase(@Nullable CompletionProgressIndicator indicator) {
    this.indicator = indicator;
  }

  @Override
  public void dispose() {
  }

  public abstract int newCompletionStarted(int time, boolean repeated);

  public boolean fillInCommonPrefix() {
    return false;
  }

  public static class AutoPopupAlarm extends CompletionPhase {
    final boolean copyCommit;
    private final Editor myEditor;
    private final Expirable focusStamp;
    private final Project myProject;

    public AutoPopupAlarm(boolean copyCommit, Editor editor) {
      super(null);
      this.copyCommit = copyCommit;
      myEditor = editor;
      myProject = editor.getProject();
      focusStamp = IdeFocusManager.getInstance(myProject).getTimestamp(false);
    }

    public boolean isExpired() {
      if (ApplicationManager.getApplication().isWriteAccessAllowed()) return false; //it will fail anyway
      return CompletionServiceImpl.getCompletionPhase() != this || focusStamp.isExpired() || DumbService.getInstance(myProject).isDumb() || myEditor.isDisposed();
    }

    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      CompletionServiceImpl.setCompletionPhase(NoCompletion);
      return time;
    }

    @Override
    public String toString() {
      return "AutoPopupAlarm{copyCommit=" + copyCommit + '}';
    }
  }
  public static class Synchronous extends CompletionPhase {
    public Synchronous(CompletionProgressIndicator indicator) {
      super(indicator);
    }

    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      CompletionServiceImpl.assertPhase(NoCompletion.getClass()); // will fail and log valuable info
      CompletionServiceImpl.setCompletionPhase(NoCompletion);
      return time;
    }
  }
  public static class BgCalculation extends CompletionPhase {
    boolean modifiersChanged = false;

    public BgCalculation(final CompletionProgressIndicator indicator) {
      super(indicator);
      ApplicationManager.getApplication().addApplicationListener(new ApplicationAdapter() {
        @Override
        public void beforeWriteActionStart(Object action) {
          indicator.scheduleRestart();
        }
      }, this);
    }

    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      indicator.closeAndFinish(false);
      return indicator.nextInvocationCount(time, repeated);
    }
  }
  public static class ItemsCalculated extends CompletionPhase {

    public ItemsCalculated(CompletionProgressIndicator indicator) {
      super(indicator);
    }

    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      indicator.closeAndFinish(false);
      indicator.restorePrefix(new Runnable() {
        @Override
        public void run() {
          indicator.getLookup().restorePrefix();
        }
      });
      return indicator.nextInvocationCount(time, repeated);
    }

    @Override
    public boolean fillInCommonPrefix() {
      return indicator.fillInCommonPrefix(true);
    }
  }
  public static class Restarted extends CompletionPhase {
    public Restarted(CompletionProgressIndicator indicator) {
      super(indicator);
    }

    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      indicator.closeAndFinish(false);
      return indicator.nextInvocationCount(time, repeated);
    }
  }

  public static abstract class ZombiePhase extends CompletionPhase {

    protected ZombiePhase(@Nullable final LightweightHint hint, final CompletionProgressIndicator indicator) {
      super(indicator);
      @NotNull Editor editor = indicator.getEditor();
      final HintListener hintListener = new HintListener() {
        public void hintHidden(final EventObject event) {
          CompletionServiceImpl.setCompletionPhase(NoCompletion);
        }
      };
      final DocumentAdapter documentListener = new DocumentAdapter() {
        @Override
        public void beforeDocumentChange(DocumentEvent e) {
          CompletionServiceImpl.setCompletionPhase(NoCompletion);
        }
      };
      final SelectionListener selectionListener = new SelectionListener() {
        public void selectionChanged(SelectionEvent e) {
          CompletionServiceImpl.setCompletionPhase(NoCompletion);
        }
      };
      final CaretListener caretListener = new CaretListener() {
        public void caretPositionChanged(CaretEvent e) {
          CompletionServiceImpl.setCompletionPhase(NoCompletion);
        }
      };

      final Document document = editor.getDocument();
      final SelectionModel selectionModel = editor.getSelectionModel();
      final CaretModel caretModel = editor.getCaretModel();


      if (hint != null) {
        hint.addHintListener(hintListener);
      }
      document.addDocumentListener(documentListener);
      selectionModel.addSelectionListener(selectionListener);
      caretModel.addCaretListener(caretListener);

      Disposer.register(this, new Disposable() {
        @Override
        public void dispose() {
          if (hint != null) {
            hint.removeHintListener(hintListener);
          }
          document.removeDocumentListener(documentListener);
          selectionModel.removeSelectionListener(selectionListener);
          caretModel.removeCaretListener(caretListener);
        }
      });
    }

  }

  public static class InsertedSingleItem extends ZombiePhase {
    public final Runnable restorePrefix;

    public InsertedSingleItem(CompletionProgressIndicator indicator, Runnable restorePrefix) {
      super(null, indicator);
      this.restorePrefix = restorePrefix;
    }

    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      CompletionServiceImpl.setCompletionPhase(NoCompletion);
      indicator.restorePrefix(restorePrefix);
      return indicator.nextInvocationCount(time, repeated);
    }

  }
  public static class NoSuggestionsHint extends ZombiePhase {
    public NoSuggestionsHint(@Nullable LightweightHint hint, CompletionProgressIndicator indicator) {
      super(hint, indicator);
    }

    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      CompletionServiceImpl.setCompletionPhase(NoCompletion);
      return indicator.nextInvocationCount(time, repeated);
    }

  }
  public static class EmptyAutoPopup extends CompletionPhase {
    public final Editor editor;
    private final Project project;
    private final EditorMouseAdapter mouseListener;
    private final CaretListener caretListener;
    private final DocumentAdapter documentListener;
    private final PropertyChangeListener lookupListener;
    private boolean changeGuard = false;
    private final SelectionListener selectionListener;

    public EmptyAutoPopup(CompletionProgressIndicator indicator) {
      super(indicator);
      this.editor = indicator.getEditor();
      this.project = indicator.getProject();
      MessageBusConnection connection = project.getMessageBus().connect(this);
      connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
        @Override
        public void selectionChanged(FileEditorManagerEvent event) {
          stopAutoPopup();
        }
      });

      mouseListener = new EditorMouseAdapter() {
        @Override
        public void mouseClicked(EditorMouseEvent e) {
          stopAutoPopup();
        }
      };

      caretListener = new CaretListener() {
        @Override
        public void caretPositionChanged(CaretEvent e) {
          if (!changeGuard) {
            stopAutoPopup();
          }
        }
      };
      selectionListener = new SelectionListener() {
        @Override
        public void selectionChanged(SelectionEvent e) {
          stopAutoPopup();
        }
      };
      documentListener = new DocumentAdapter() {
        @Override
        public void documentChanged(DocumentEvent e) {
          if (!changeGuard) {
            stopAutoPopup();
          }
        }
      };
      lookupListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          stopAutoPopup();
        }
      };

      editor.addEditorMouseListener(mouseListener);
      editor.getCaretModel().addCaretListener(caretListener);
      editor.getDocument().addDocumentListener(documentListener);
      editor.getSelectionModel().addSelectionListener(selectionListener);
      LookupManager.getInstance(project).addPropertyChangeListener(lookupListener);
    }

    @Override
    public void dispose() {
      editor.removeEditorMouseListener(mouseListener);
      editor.getCaretModel().removeCaretListener(caretListener);
      editor.getSelectionModel().removeSelectionListener(selectionListener);
      editor.getDocument().removeDocumentListener(documentListener);
      LookupManager.getInstance(project).removePropertyChangeListener(lookupListener);
    }

    public void handleTyping(char c) {
      changeGuard = true;
      try {
        EditorModificationUtil.typeInStringAtCaretHonorBlockSelection(editor, String.valueOf(c), true);
      }
      finally {
        changeGuard = false;
      }
    }

    private static void stopAutoPopup() {
      CompletionServiceImpl.setCompletionPhase(NoCompletion);
    }

    @Override
    public String toString() {
      return "EmptyAutoPopup,editor=" + editor;
    }

    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      CompletionServiceImpl.setCompletionPhase(NoCompletion);
      return time;
    }
  }

}
