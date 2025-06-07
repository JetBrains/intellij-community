// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl;

import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

public final class GoToChangePopupBuilder {
  public interface Chain extends DiffRequestChain {
    @Nullable
    AnAction createGoToChangeAction(@NotNull Consumer<? super Integer> onSelected, int defaultSelection);
  }

  public static @NotNull AnAction create(@NotNull DiffRequestChain chain, @NotNull Consumer<? super Integer> onSelected, int defaultSelection) {
    if (chain instanceof Chain) {
      AnAction action = ((Chain)chain).createGoToChangeAction(onSelected, defaultSelection);
      if (action != null) return action;
    }
    return new SimpleGoToChangePopupAction(chain, onSelected, defaultSelection);
  }

  public abstract static class BaseGoToChangePopupAction extends GoToChangePopupAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(canNavigate() && e.getData(DiffDataKeys.DIFF_CONTEXT) != null);
    }

    protected abstract boolean canNavigate();

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final JBPopup popup = createPopup(e);

      InputEvent event = e.getInputEvent();
      if (event instanceof MouseEvent) {
        popup.showUnderneathOf(event.getComponent());
      }
      else {
        popup.showInBestPositionFor(e.getDataContext());
      }
    }

    protected abstract @NotNull JBPopup createPopup(@NotNull AnActionEvent e);
  }

  private static class SimpleGoToChangePopupAction extends BaseGoToChangePopupAction {
    private final DiffRequestChain myChain;
    private final @NotNull Consumer<? super Integer> myOnSelected;
    private final int myDefaultSelection;

    SimpleGoToChangePopupAction(@NotNull DiffRequestChain chain, @NotNull Consumer<? super Integer> onSelected, int defaultSelection) {
      myChain = chain;
      myOnSelected = onSelected;
      myDefaultSelection = defaultSelection;
    }

    @Override
    protected boolean canNavigate() {
      return myChain.getRequests().size() > 1;
    }

    @Override
    protected @NotNull JBPopup createPopup(@NotNull AnActionEvent e) {
      return JBPopupFactory.getInstance().createListPopup(new MyListPopupStep());
    }

    private class MyListPopupStep extends BaseListPopupStep<DiffRequestProducer> {
      MyListPopupStep() {
        super(DiffBundle.message("action.presentation.go.to.change.text"), myChain.getRequests());
        setDefaultOptionIndex(myDefaultSelection);
      }

      @Override
      public @NotNull String getTextFor(DiffRequestProducer value) {
        return value.getName();
      }

      @Override
      public boolean isSpeedSearchEnabled() {
        return true;
      }

      @Override
      public PopupStep<?> onChosen(final DiffRequestProducer selectedValue, boolean finalChoice) {
        return doFinalStep(() -> {
          int index = myChain.getRequests().indexOf(selectedValue);
          myOnSelected.consume(index);
        });
      }
    }
  }
}
