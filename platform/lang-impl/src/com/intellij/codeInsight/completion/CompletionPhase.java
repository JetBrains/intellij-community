/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Expirable;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public static class CommittingDocuments extends CompletionPhase {
    boolean replaced;
    private boolean actionsHappened;
    private final Editor myEditor;
    private final Expirable focusStamp;
    private final Project myProject;
    private boolean ignoreDocumentChanges;

    public CommittingDocuments(@Nullable CompletionProgressIndicator prevIndicator, Editor editor) {
      super(prevIndicator);
      myEditor = editor;
      myProject = editor.getProject();
      focusStamp = IdeFocusManager.getInstance(myProject).getTimestamp(true);
      ActionManager.getInstance().addAnActionListener(new AnActionListener.Adapter() {
        @Override
        public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
          actionsHappened = true;
        }
      }, this);
      myEditor.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        public void documentChanged(DocumentEvent e) {
          if (!ignoreDocumentChanges) {
            actionsHappened = true;
          }
        }
      }, this);
    }

    public void ignoreCurrentDocumentChange() {
      ignoreDocumentChanges = true;
      CommandProcessor.getInstance().addCommandListener(new CommandAdapter() {
        @Override
        public void commandFinished(CommandEvent event) {
          CommandProcessor.getInstance().removeCommandListener(this);
          ignoreDocumentChanges = false;
        }
      });
    }

    public boolean isRestartingCompletion() {
      return indicator != null;
    }

    public boolean checkExpired() {
      if (CompletionServiceImpl.getCompletionPhase() != this) {
        return true;
      }

      if (actionsHappened || focusStamp.isExpired() || DumbService.getInstance(myProject).isDumb() ||
          myEditor.isDisposed() ||
          (myEditor instanceof EditorWindow && !((EditorWindow)myEditor).isValid()) ||
          ApplicationManager.getApplication().isWriteAccessAllowed()) {
        CompletionServiceImpl.setCompletionPhase(NoCompletion);
        return true;
      }

      return false;
    }

    @Override
    public int newCompletionStarted(int time, boolean repeated) {
      return time;
    }

    @Override
    public void dispose() {
      if (!replaced && indicator != null) {
        indicator.closeAndFinish(true);
      }
    }

    @Override
    public String toString() {
      return "CommittingDocuments{hasIndicator=" + (indicator != null) + '}';
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
        public void beforeWriteActionStart(@NotNull Object action) {
          if (!indicator.getLookup().isLookupDisposed() && !indicator.isCanceled()) {
            indicator.scheduleRestart();
          }
        }
      }, this);
      if (indicator.isAutopopupCompletion()) {
        // lookup is not visible, we have to check ourselves if editor retains focus
        ((EditorEx)indicator.getEditor()).addFocusListener(new FocusChangeListener() {
          @Override
          public void focusGained(Editor editor) {
          }

          @Override
          public void focusLost(Editor editor) {
            indicator.closeAndFinish(true);
          }
        }, this);
      }
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
      if (indicator.isAutopopupCompletion()) {
        return false;
      }

      return indicator.fillInCommonPrefix(true);
    }
  }

  public static abstract class ZombiePhase extends CompletionPhase {

    protected ZombiePhase(@Nullable final LightweightHint hint, final CompletionProgressIndicator indicator) {
      super(indicator);
      @NotNull Editor editor = indicator.getEditor();
      final HintListener hintListener = new HintListener() {
        @Override
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
        @Override
        public void selectionChanged(SelectionEvent e) {
          CompletionServiceImpl.setCompletionPhase(NoCompletion);
        }
      };
      final CaretListener caretListener = new CaretAdapter() {
        @Override
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

}
