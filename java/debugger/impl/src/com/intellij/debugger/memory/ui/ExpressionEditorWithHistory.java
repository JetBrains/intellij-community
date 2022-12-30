// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.ui;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

class ExpressionEditorWithHistory extends XDebuggerExpressionEditor {
  private static final String HISTORY_ID_PREFIX = "filtering";

  ExpressionEditorWithHistory(final @NotNull Project project,
                              final @NotNull String className,
                              final @NotNull XDebuggerEditorsProvider debuggerEditorsProvider,
                              final @Nullable Disposable parentDisposable) {
    super(project, debuggerEditorsProvider, HISTORY_ID_PREFIX + className, null,
          XExpressionImpl.EMPTY_EXPRESSION, false, true, true);

    new AnAction(JavaDebuggerBundle.message("instances.window.show.history")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        showHistory();
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(LookupManager.getActiveLookup(getEditor()) == null);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("DOWN"), getComponent(), parentDisposable);

    ReadAction.nonBlocking(() -> DebuggerUtils.findClass(className, project, GlobalSearchScope.allScope(project)))
      .finishOnUiThread(ModalityState.defaultModalityState(), context -> {
        if (context != null) {
          setContext(context);
        }
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private void showHistory() {
    List<XExpression> expressions = getRecentExpressions();
    if (!expressions.isEmpty()) {
      ListPopupImpl historyPopup = new ListPopupImpl(getProject(), new BaseListPopupStep<>(null, expressions) {
        @Override
        public PopupStep onChosen(XExpression selectedValue, boolean finalChoice) {
          setExpression(selectedValue);
          requestFocusInEditor();
          return FINAL_CHOICE;
        }
      }) {
        @Override
        protected ListCellRenderer getListElementRenderer() {
          return SimpleListCellRenderer.create("", XExpression::getExpression);
        }
      };

      historyPopup.getList().setFont(EditorUtil.getEditorFont());
      historyPopup.showUnderneathOf(getEditorComponent());
    }
  }
}
