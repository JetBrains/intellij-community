/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.documentation;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.content.*;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * User: anna
 * Date: 5/7/12
 */
public abstract class DockablePopupManager<T extends JComponent & Disposable> {
  protected ToolWindow myToolWindow = null;
  protected boolean myAutoUpdateDocumentation = PropertiesComponent.getInstance().isTrueValue(getAutoUpdateEnabledProperty());
  protected Runnable myAutoUpdateRequest;
  @NotNull protected final Project myProject;

  public DockablePopupManager(@NotNull Project project) {
    myProject = project;
  }

  protected abstract String getShowInToolWindowProperty();
  protected abstract String getAutoUpdateEnabledProperty();

  protected abstract String getAutoUpdateTitle();
  protected abstract String getRestorePopupDescription();
  protected abstract String getAutoUpdateDescription();

  protected abstract T createComponent();
  protected abstract void doUpdateComponent(PsiElement element, PsiElement originalElement, T component);
  protected abstract void doUpdateComponent(Editor editor, PsiFile psiFile);
  protected abstract void doUpdateComponent(@NotNull PsiElement element);

  protected abstract String getTitle(PsiElement element);
  protected abstract String getToolwindowId();
  
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

  public void createToolWindow(final PsiElement element, PsiElement originalElement) {
    assert myToolWindow == null;

    final T component = createComponent();

    final ToolWindowManagerEx toolWindowManagerEx = ToolWindowManagerEx.getInstanceEx(myProject);
    final ToolWindow toolWindow = toolWindowManagerEx.getToolWindow(getToolwindowId());
    myToolWindow = toolWindow == null
                   ? toolWindowManagerEx.registerToolWindow(getToolwindowId(), true, ToolWindowAnchor.RIGHT, myProject)
                   : toolWindow;
    myToolWindow.setIcon(AllIcons.Toolwindows.Documentation);

    myToolWindow.setAvailable(true, null);
    myToolWindow.setToHideOnEmptyContent(false);

    final Rectangle rectangle = WindowManager.getInstance().getIdeFrame(myProject).suggestChildFrameBounds();
    myToolWindow.setDefaultState(ToolWindowAnchor.RIGHT, ToolWindowType.FLOATING, rectangle);

    final ContentManager contentManager = myToolWindow.getContentManager();
    final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    final Content content = contentFactory.createContent(component, getTitle(element), false);
    contentManager.addContent(content);

    contentManager.addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void contentRemoved(ContentManagerEvent event) {
        restorePopupBehavior();
      }
    });

    new UiNotifyConnector(component, new Activatable() {
      @Override
      public void showNotify() {
        restartAutoUpdate(myAutoUpdateDocumentation);
      }

      @Override
      public void hideNotify() {
        restartAutoUpdate(false);
      }
    });

    myToolWindow.show(null);
    PropertiesComponent.getInstance().setValue(getShowInToolWindowProperty(), Boolean.TRUE.toString());
    restartAutoUpdate(PropertiesComponent.getInstance().getBoolean(getAutoUpdateEnabledProperty(), true));
    doUpdateComponent(element, originalElement, component);
  }


  protected AnAction[] createActions() {
    return new AnAction[]{
      new ToggleAction(getAutoUpdateTitle(), getAutoUpdateDescription(),
                       AllIcons.General.AutoscrollFromSource) {
        @Override
        public boolean isSelected(AnActionEvent e) {
          return myAutoUpdateDocumentation;
        }

        @Override
        public void setSelected(AnActionEvent e, boolean state) {
          PropertiesComponent.getInstance().setValue(getAutoUpdateEnabledProperty(), String.valueOf(state));
          myAutoUpdateDocumentation = state;
          restartAutoUpdate(state);
        }
      },
      new AnAction("Restore Popup", getRestorePopupDescription(), AllIcons.Actions.Cancel) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          restorePopupBehavior();
        }
      }};
  }

  
  protected void restartAutoUpdate(final boolean state) {
    if (state && myToolWindow != null) {
      if (myAutoUpdateRequest == null) {
        myAutoUpdateRequest = new Runnable() {
          @Override
          public void run() {
            updateComponent();
          }
        };

        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            IdeEventQueue.getInstance().addIdleListener(myAutoUpdateRequest, 500);
          }
        });
      }
    }
    else {
      if (myAutoUpdateRequest != null) {
        IdeEventQueue.getInstance().removeIdleListener(myAutoUpdateRequest);
        myAutoUpdateRequest = null;
      }
    }
  }

  public void updateComponent() {
    if (myProject.isDisposed()) return;

    AsyncResult<DataContext> asyncResult = DataManager.getInstance().getDataContextFromFocus();
    DataContext dataContext = asyncResult.getResult();
    if (dataContext == null) {
      return;
    }

    if (CommonDataKeys.PROJECT.getData(dataContext) != myProject) {
      return;
    }

    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
      if (element != null) {
        doUpdateComponent(element);
      }
      return;
    }

    final PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, myProject);

    final Editor injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file);
    if (injectedEditor != null) {
      final PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(injectedEditor, myProject);
      if (psiFile != null) {
        doUpdateComponent(injectedEditor, psiFile);
        return;
      }
    }

    if (file != null) {
      doUpdateComponent(editor, file);
    }
  }


  protected void restorePopupBehavior() {
    if (myToolWindow != null) {
      PropertiesComponent.getInstance().setValue(getShowInToolWindowProperty(), Boolean.FALSE.toString());

      final Content[] contents = myToolWindow.getContentManager().getContents();
      for (final Content content : contents) {
        myToolWindow.getContentManager().removeContent(content, true);
      }

      ToolWindowManagerEx.getInstanceEx(myProject).unregisterToolWindow(getToolwindowId());
      myToolWindow = null;
      restartAutoUpdate(false);
    }
  }

  public boolean hasActiveDockedDocWindow() {
    return myToolWindow != null && myToolWindow.isVisible();
  }
}
