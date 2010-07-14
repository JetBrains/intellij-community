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

package com.intellij.execution.ui.layout.impl;

import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.LayoutAttractionPolicy;
import com.intellij.execution.ui.layout.LayoutStateDefaults;
import com.intellij.execution.ui.layout.LayoutViewOptions;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.ui.switcher.SwitchProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class RunnerLayoutUiImpl implements Disposable, RunnerLayoutUi, LayoutStateDefaults, LayoutViewOptions {
  private final RunnerLayout myLayout;
  private final JPanel myContentPanel;
  private final RunnerContentUi myContentUI;

  private final ContentManager myViewsContentManager;
  public static final Key<String> CONTENT_TYPE = Key.create("ContentType");

  public RunnerLayoutUiImpl(Project project, Disposable parent, String runnerType, String runnerTitle, String sessionName) {
    myLayout = RunnerLayoutSettings.getInstance().getLayout(runnerType);
    Disposer.register(parent, this);

    myContentUI = new RunnerContentUi(project, this, ActionManager.getInstance(), IdeFocusManager.getInstance(project), myLayout,
                                           runnerTitle + " - " + sessionName);

    myContentPanel = new MyContent();

    myViewsContentManager = getContentFactory().createContentManager(myContentUI.getContentUI(), false, project);
    Disposer.register(this, myViewsContentManager);

    myContentPanel.add(myViewsContentManager.getComponent(), BorderLayout.CENTER);
  }

  @NotNull
  public LayoutViewOptions setTopToolbar(@NotNull ActionGroup actions, @NotNull String place) {
    myContentUI.setTopActions(actions, place);
    return this;
  }


  public LayoutStateDefaults initTabDefaults(int id, String text, Icon icon) {
    getLayout().setDefault(id, text, icon);
    return this;
  }

  public LayoutStateDefaults initFocusContent(@NotNull final String id, @NotNull final String condition) {
    return initFocusContent(id, condition, new LayoutAttractionPolicy.FocusOnce());
  }

  public LayoutStateDefaults initFocusContent(@NotNull final String id, @NotNull final String condition, @NotNull final LayoutAttractionPolicy policy) {
    getLayout().setDefaultToFocus(id, condition, policy);
    return this;
  }

  @NotNull
  public Content addContent(@NotNull Content content) {
    return addContent(content, false, -1, PlaceInGrid.center, false);
  }

  @NotNull
  public Content addContent(@NotNull Content content, int defaultTabId, PlaceInGrid defaultPlace, boolean defaultIsMinimized) {
    return addContent(content, true, defaultTabId, defaultPlace, defaultIsMinimized);
  }

  public Content addContent(Content content, boolean applyDefaults, int defaultTabId, PlaceInGrid defaultPlace, boolean defaultIsMinimized) {
    final String id = content.getUserData(CONTENT_TYPE);

    assert id != null : "Content id is missing, use RunnerLayoutUi to create content instances";

    if (applyDefaults) {
      getLayout().setDefault(id, defaultTabId, defaultPlace, defaultIsMinimized);
    }

    getContentManager().addContent(content);
    return content;
  }

  @NotNull
  public Content createContent(@NotNull String id, @NotNull JComponent component, @NotNull String displayName, @Nullable Icon icon, @Nullable JComponent focusable) {
    return createContent(id, new ComponentWithActions.Impl(component), displayName, icon, focusable);
  }

  @NotNull
  public Content createContent(@NotNull final String contentId, @NotNull final ComponentWithActions withActions, @NotNull final String displayName,
                               @Nullable final Icon icon,
                               @Nullable final JComponent toFocus) {
    final Content content = getContentFactory().createContent(withActions.getComponent(), displayName, false);
    content.putUserData(CONTENT_TYPE, contentId);
    content.putUserData(ViewImpl.ID, contentId);
    content.setIcon(icon);
    if (toFocus != null) {
      content.setPreferredFocusableComponent(toFocus);
    }

    if (!withActions.isContentBuiltIn()) {
      content.setSearchComponent(withActions.getSearchComponent());
      content.setActions(withActions.getToolbarActions(), withActions.getToolbarPlace(), withActions.getToolbarContextComponent());
    }

    return content;
  }

  @NotNull
  public JComponent getComponent() {
    return myContentPanel;
  }

  private static ContentFactory getContentFactory() {
    return ContentFactory.SERVICE.getInstance();
  }

  public RunnerLayout getLayout() {
    return myLayout;
  }

  public void updateActionsNow() {
    myContentUI.updateActionsImmediately();
  }

  public void dispose() {
  }

  @NotNull
  public ContentManager getContentManager() {
    return myViewsContentManager;
  }

  public ActionCallback selectAndFocus(@Nullable final Content content, boolean requestFocus, final boolean forced) {
    return selectAndFocus(content, requestFocus, forced, false);
  }

  public ActionCallback selectAndFocus(@Nullable final Content content, boolean requestFocus, final boolean forced, boolean implicit) {
    if (content == null) return new ActionCallback.Rejected();
    return getContentManager().setSelectedContent(content, requestFocus || shouldRequestFocus(), forced, implicit);
  }

  private boolean shouldRequestFocus() {
    final Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return focused != null && SwingUtilities.isDescendingFrom(focused, getContentManager().getComponent());
  }

  public boolean removeContent(final Content content, final boolean dispose) {
    if (content == null) return false;
    return getContentManager().removeContent(content, dispose);
  }

  public boolean isToFocus(final Content content, final String condition) {
    final String id = content.getUserData(ViewImpl.ID);
    return getLayout().isToFocus(id, condition);
  }

  public LayoutViewOptions setToFocus(@Nullable final Content content, final String condition) {
    getLayout().setToFocus(content != null ? content.getUserData(ViewImpl.ID) : null, condition);
    return this;
  }

  public void attractBy(@NotNull final String condition) {
    myContentUI.attractByCondition(condition, true);
  }

  public void clearAttractionBy(final String condition) {
    myContentUI.clearAttractionByCondition(condition, true);
  }

  public void removeContent(String id, final boolean dispose) {
    final Content content = findContent(id);
    if (content != null) {
      getContentManager().removeContent(content, dispose);
    }
  }

  public AnAction getLayoutActions() {
    return myContentUI.getLayoutActions();
  }

  public AnAction[] getLayoutActionsList() {
    final ActionGroup group = (ActionGroup)getLayoutActions();
    return group.getChildren(null);
  }

  public LayoutViewOptions setLeftToolbar(@NotNull final ActionGroup leftToolbar, @NotNull final String place) {
    myContentUI.setLeftToolbar(leftToolbar, place);
    return this;
  }

  @Nullable
  public Content findContent(@NotNull final String key) {
    return myContentUI.findContent(key);
  }

  public RunnerLayoutUi addListener(@NotNull final ContentManagerListener listener, @NotNull final Disposable parent) {
    final ContentManager mgr = getContentManager();
    mgr.addContentManagerListener(listener);
    Disposer.register(parent, new Disposable() {
      public void dispose() {
        mgr.removeContentManagerListener(listener);
      }
    });
    return this;
  }

  public void removeListener(@NotNull final ContentManagerListener listener) {
    getContentManager().removeContentManagerListener(listener);
  }

  public void setBouncing(@NotNull final Content content, final boolean activate) {
    myContentUI.processBounce(content, activate);
  }


  public boolean isDisposed() {
    return getContentManager().isDisposed();
  }

  @NotNull
  public LayoutViewOptions setMinimizeActionEnabled(final boolean enabled) {
    myContentUI.setMinimizeActionEnabled(enabled);
    return this;
  }

  public LayoutViewOptions setToDisposeRemoveContent(boolean toDispose) {
    myContentUI.setToDisposeRemovedContent(toDispose);
    return this;
  }

  @NotNull
  public LayoutViewOptions setMoveToGridActionEnabled(final boolean enabled) {
    myContentUI.setMovetoGridActionEnabled(enabled);
    return this;
  }

  @NotNull
  public LayoutViewOptions setAttractionPolicy(@NotNull final String contentId, final LayoutAttractionPolicy policy) {
    myContentUI.setPolicy(contentId, policy);
    return this;
  }

  public LayoutViewOptions setConditionAttractionPolicy(@NotNull final String condition, final LayoutAttractionPolicy policy) {
    myContentUI.setConditionPolicy(condition, policy);
    return this;
  }

  @NotNull
  public LayoutStateDefaults getDefaults() {
    return this;
  }

  @NotNull
  public LayoutViewOptions getOptions() {
    return this;
  }

  public LayoutViewOptions setAdditionalFocusActions(final ActionGroup group) {
    myContentUI.setAdditionalFocusActions(group);
    return this;
  }

  public Content[] getContents() {
    Content[] contents = new Content[getContentManager().getContentCount()];
    for (int i = 0; i < contents.length; i++) {
      contents[i] = getContentManager().getContent(i);
    }
    return contents;
  }

  private class MyContent extends JPanel implements DataProvider {
    public MyContent() {
      super(new BorderLayout());
    }

    public Object getData(@NonNls String dataId) {
      if (SwitchProvider.KEY.getName().equals(dataId)) {
        return myContentUI;
      }

      if (QuickActionProvider.KEY.getName().equals(dataId)) {
        return myContentUI;
      }

      if (RunnerContentUi.KEY.getName().equals(dataId)) {
        return myContentUI;
      }

      return null;
    }
  }

}
