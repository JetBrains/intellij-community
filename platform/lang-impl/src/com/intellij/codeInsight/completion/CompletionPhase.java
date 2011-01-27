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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.util.Disposer;
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
    public CompletionProgressIndicator newCompletionStarted() {
      return null;
    }
  };

  public final CompletionProgressIndicator indicator;

  protected CompletionPhase(CompletionProgressIndicator indicator) {
    this.indicator = indicator;
  }

  @Override
  public void dispose() {
  }

  @Nullable
  public abstract CompletionProgressIndicator newCompletionStarted();

  public static class AutoPopupAlarm extends CompletionPhase {
    public AutoPopupAlarm() {
      super(null);
    }

    @Override
    public CompletionProgressIndicator newCompletionStarted() {
      return null;
    }
  }
  public static class Synchronous extends CompletionPhase {
    public Synchronous(CompletionProgressIndicator indicator) {
      super(indicator);
    }

    @Override
    public CompletionProgressIndicator newCompletionStarted() {
      throw new UnsupportedOperationException("Not implemented");
    }
  }
  public static class BgCalculation extends CompletionPhase {
    public BgCalculation(CompletionProgressIndicator indicator) {
      super(indicator);
    }

    void focusLookupWhenDone() {
      Disposer.register(this, new Disposable() {
        @Override
        public void dispose() {
          indicator.getLookup().setFocused(true);
        }
      });
    }

    @Override
    public CompletionProgressIndicator newCompletionStarted() {
      indicator.closeAndFinish(false);
      return indicator;
    }
  }
  public static class ItemsCalculated extends CompletionPhase {
    public ItemsCalculated(CompletionProgressIndicator indicator) {
      super(indicator);
    }

    @Override
    public CompletionProgressIndicator newCompletionStarted() {
      indicator.closeAndFinish(false);
      return indicator;
    }
  }
  public static class Restarted extends CompletionPhase {
    public Restarted(CompletionProgressIndicator indicator) {
      super(indicator);
    }

    @Override
    public CompletionProgressIndicator newCompletionStarted() {
      indicator.closeAndFinish(false);
      return indicator;
    }
  }

  public static class ZombiePhase extends CompletionPhase {

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

    @Override
    public CompletionProgressIndicator newCompletionStarted() {
      return indicator;
    }
  }

  public static class InsertedSingleItem extends ZombiePhase {
    public final Runnable restorePrefix;

    public InsertedSingleItem(CompletionProgressIndicator indicator, Runnable restorePrefix) {
      super(null, indicator);
      this.restorePrefix = restorePrefix;
    }
  }
  public static class NoSuggestionsHint extends ZombiePhase {
    public NoSuggestionsHint(@Nullable LightweightHint hint, CompletionProgressIndicator indicator) {
      super(hint, indicator);
    }
  }
  public static class PossiblyDisturbingAutoPopup extends CompletionPhase {
    public PossiblyDisturbingAutoPopup(CompletionProgressIndicator indicator) {
      super(indicator);
    }

    @Override
    public CompletionProgressIndicator newCompletionStarted() {
      return null;
    }
  }
  public static class EmptyAutoPopup extends CompletionPhase {
    public EmptyAutoPopup(CompletionProgressIndicator indicator) {
      super(indicator);
    }

    @Override
    public CompletionProgressIndicator newCompletionStarted() {
      return null;
    }
  }

}
