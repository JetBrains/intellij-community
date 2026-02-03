// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.content.*;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @deprecated Not supported anymore.
 */
@Deprecated
public abstract class DockablePopupManager<T extends JComponent & Disposable> {
  private static final Logger LOG = Logger.getInstance(DockablePopupManager.class);
  protected ToolWindow myToolWindow;
  private Runnable myAutoUpdateRequest;
  private boolean myAutoUpdateMuted;
  protected final @NotNull Project myProject;

  public DockablePopupManager(@NotNull Project project) {
    myProject = project;
  }

  protected abstract @NonNls String getShowInToolWindowProperty();

  protected abstract @NonNls String getAutoUpdateEnabledProperty();

  protected boolean getAutoUpdateDefault() {
    return false;
  }

  protected abstract @Nls String getAutoUpdateTitle();

  protected abstract @Nls String getRestorePopupDescription();

  protected abstract @Nls String getAutoUpdateDescription();

  protected abstract T createComponent();

  protected void doUpdateComponent(@NotNull CompletableFuture<? extends PsiElement> elementFuture, PsiElement originalElement, T component) {
    doUpdateComponent(elementFuture, originalElement, component, false);
  }

  protected void doUpdateComponent(@NotNull CompletableFuture<? extends PsiElement> elementFuture,
                                   PsiElement originalElement,
                                   T component,
                                   boolean onAutoUpdate) {
    try {
      doUpdateComponent(elementFuture.get(), originalElement, component, onAutoUpdate);
    }
    catch (InterruptedException | ExecutionException e) {
      LOG.debug("Cannot update component", e);
    }
  }

  protected abstract void doUpdateComponent(@NotNull PsiElement element, PsiElement originalElement, T component);

  protected void doUpdateComponent(@NotNull PsiElement element, PsiElement originalElement, T component, boolean onAutoUpdate) {
    doUpdateComponent(element, originalElement, component);
  }

  protected void doUpdateComponent(Editor editor, PsiFile psiFile, boolean requestFocus) { doUpdateComponent(editor, psiFile); }

  protected void doUpdateComponent(Editor editor, PsiFile psiFile, boolean requestFocus, boolean onAutoUpdate) {
    doUpdateComponent(editor, psiFile, requestFocus);
  }

  protected abstract void doUpdateComponent(Editor editor, PsiFile psiFile);

  protected abstract void doUpdateComponent(@NotNull PsiElement element);

  protected void doUpdateComponent(@NotNull PsiElement element, boolean onAutoUpdate) {
    doUpdateComponent(element);
  }

  protected abstract @NlsContexts.TabTitle String getTitle(PsiElement element);

  protected abstract String getToolwindowId();

  protected @NlsContexts.TabTitle String getToolwindowTitle() {
    LOG.error(getClass().getName() + " should override getToolwindowTitle() method");
    //noinspection HardCodedStringLiteral
    return getToolwindowId(); // fallback for API compatibility
  }

  public Content recreateToolWindow(PsiElement element, PsiElement originalElement) {
    if (myToolWindow == null) {
      createToolWindow(element, originalElement);
      return null;
    }

    final Content content = myToolWindow.getContentManager().getSelectedContent();
    if (content == null || !myToolWindow.isVisible()) {
      restorePopupBehavior();
      createToolWindow(element, originalElement);
      return null;
    }
    return content;
  }

  public void createToolWindow(@NotNull PsiElement element, PsiElement originalElement) {
    doCreateToolWindow(element, null, originalElement);
  }

  public void createToolWindow(@NotNull CompletableFuture<PsiElement> elementFuture, PsiElement originalElement) {
    doCreateToolWindow(null, elementFuture, originalElement);
  }

  private void doCreateToolWindow(@Nullable PsiElement element,
                                  @Nullable CompletableFuture<PsiElement> elementFuture,
                                  PsiElement originalElement) {
    assert myToolWindow == null;

    T component = createComponent();

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow = toolWindowManager.getToolWindow(getToolwindowId());
    if (toolWindow == null) {
      toolWindow = toolWindowManager
        .registerToolWindow(RegisterToolWindowTask.closable(getToolwindowId(), this::getToolwindowTitle,
                                                            AllIcons.Toolwindows.Documentation, ToolWindowAnchor.RIGHT));
    }
    else {
      toolWindow.setAvailable(true);
    }
    myToolWindow = toolWindow;

    toolWindow.setToHideOnEmptyContent(false);

    setToolwindowDefaultState(myToolWindow);

    ContentManager contentManager = toolWindow.getContentManager();
    String displayName = element != null ? getTitle(element) : "";
    contentManager.addContent(ContentFactory.getInstance().createContent(component, displayName, false));
    contentManager.addContentManagerListener(new ContentManagerListener() {
      @Override
      public void contentRemoved(@NotNull ContentManagerEvent event) {
        restorePopupBehavior();
      }
    });

    installComponentActions(toolWindow, component);

    UiNotifyConnector.installOn(component, new Activatable() {
      @Override
      public void showNotify() {
        restartAutoUpdate(PropertiesComponent.getInstance().getBoolean(getAutoUpdateEnabledProperty(), getAutoUpdateDefault()));
      }

      @Override
      public void hideNotify() {
        restartAutoUpdate(false);
      }
    });

    myToolWindow.show(null);
    PropertiesComponent.getInstance().setValue(getShowInToolWindowProperty(), Boolean.TRUE.toString());
    restartAutoUpdate(PropertiesComponent.getInstance().getBoolean(getAutoUpdateEnabledProperty(), true));
    if (element != null) {
      doUpdateComponent(element, originalElement, component, false);
    }
    else {
      //noinspection ConstantConditions
      doUpdateComponent(elementFuture, originalElement, component, false);
    }
  }

  protected void installComponentActions(@NotNull ToolWindow toolWindow, T component) {
    toolWindow.setAdditionalGearActions(new DefaultActionGroup(createActions()));
  }

  protected void setToolwindowDefaultState(@NotNull ToolWindow toolWindow) {
    Rectangle rectangle = WindowManager.getInstance().getIdeFrame(myProject).suggestChildFrameBounds();
    toolWindow.setDefaultState(ToolWindowAnchor.RIGHT, ToolWindowType.FLOATING, rectangle);
  }

  protected AnAction[] createActions() {
    ToggleAction toggleAutoUpdateAction = new ToggleAction(getAutoUpdateTitle(), getAutoUpdateDescription(),
                                           AllIcons.General.AutoscrollFromSource) {
      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return PropertiesComponent.getInstance().getBoolean(getAutoUpdateEnabledProperty(),
                                                            getAutoUpdateDefault());
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }
      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        PropertiesComponent.getInstance().setValue(getAutoUpdateEnabledProperty(), state, getAutoUpdateDefault());
        restartAutoUpdate(state);
      }
    };
    return new AnAction[]{createRestorePopupAction(), toggleAutoUpdateAction};
  }

  protected @NotNull AnAction createRestorePopupAction() {
    return new DumbAwareAction(CodeInsightBundle.messagePointer("action.AnActionButton.text.open.as.popup"), () -> getRestorePopupDescription(),
                               (Icon)null) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        restorePopupBehavior();
      }
    };
  }

  void restartAutoUpdate(final boolean state) {
    boolean enabled = state && myToolWindow != null && !myAutoUpdateMuted;
    if (enabled) {
      if (myAutoUpdateRequest == null) {
        myAutoUpdateRequest = () -> updateComponent(false, true);

        UIUtil.invokeLaterIfNeeded(() -> IdeEventQueue.getInstance().addIdleListener(myAutoUpdateRequest, 500));
      }
    }
    else {
      if (myAutoUpdateRequest != null) {
        IdeEventQueue.getInstance().removeIdleListener(myAutoUpdateRequest);
        myAutoUpdateRequest = null;
      }
    }
  }

  public void muteAutoUpdateTill(@NotNull Disposable disposable) {
    ThreadingAssertions.assertEventDispatchThread();
    myAutoUpdateMuted = true;
    resetAutoUpdateState();
    Disposer.register(disposable, () -> {
      ThreadingAssertions.assertEventDispatchThread();
      myAutoUpdateMuted = false;
      resetAutoUpdateState();
    });
  }

  public void resetAutoUpdateState() {
    restartAutoUpdate(PropertiesComponent.getInstance().getBoolean(getAutoUpdateEnabledProperty(), getAutoUpdateDefault()));
  }

  public void updateComponent() {
    updateComponent(false);
  }

  public void updateComponent(boolean requestFocus) {
    updateComponent(requestFocus, false);
  }

  protected void updateComponent(boolean requestFocus, boolean onAutoUpdate) {
    if (myProject.isDisposed()) {
      return;
    }

    DataManager.getInstance()
      .getDataContextFromFocusAsync()
      .onSuccess(dataContext -> {
        if (!myProject.isOpen()) return;
        updateComponentInner(dataContext, requestFocus, onAutoUpdate);
      });
  }

  private void updateComponentInner(@NotNull DataContext dataContext, boolean requestFocus, boolean onAutoUpdate) {
    if (CommonDataKeys.PROJECT.getData(dataContext) != myProject) {
      return;
    }

    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
      if (element != null) {
        doUpdateComponent(element, onAutoUpdate);
      }
      return;
    }

    PsiDocumentManager.getInstance(myProject).performLaterWhenAllCommitted(() -> {
      if (editor.isDisposed()) {
        return;
      }

      PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, myProject);
      Editor injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file);
      PsiFile injectedFile = PsiUtilBase.getPsiFileInEditor(injectedEditor, myProject);
      if (injectedFile != null) {
        doUpdateComponent(injectedEditor, injectedFile, requestFocus, onAutoUpdate);
      }
      else if (file != null) {
        doUpdateComponent(editor, file, requestFocus, onAutoUpdate);
      }
    });
  }


  public void restorePopupBehavior() {
    ToolWindow toolWindow = myToolWindow;
    if (toolWindow == null) {
      return;
    }

    PropertiesComponent.getInstance().setValue(getShowInToolWindowProperty(), Boolean.FALSE.toString());
    toolWindow.remove();
    Disposer.dispose(toolWindow.getContentManager());
    myToolWindow = null;
    restartAutoUpdate(false);
  }

  public boolean hasActiveDockedDocWindow() {
    return myToolWindow != null && myToolWindow.isVisible();
  }
}
