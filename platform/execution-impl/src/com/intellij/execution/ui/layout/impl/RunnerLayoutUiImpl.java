// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.actionSystem.EdtNoGetDataProvider;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class RunnerLayoutUiImpl implements Disposable.Parent, RunnerLayoutUi, LayoutStateDefaults, LayoutViewOptions {
  private final RunnerLayout myLayout;
  private final RunnerContentUi myContentUI;

  private final ContentManager myViewsContentManager;
  public static final Key<String> CONTENT_TYPE = Key.create("ContentType");

  public RunnerLayoutUiImpl(@NotNull Project project,
                            @NotNull Disposable parent,
                            @NotNull String runnerId,
                            @NotNull String runnerTitle,
                            @NotNull String sessionName) {
    myLayout = RunnerLayoutSettings.getInstance().getLayout(runnerId);
    Disposer.register(parent, this);

    myContentUI = new RunnerContentUi(project, this, ActionManager.getInstance(), IdeFocusManager.getInstance(project), myLayout,
                                      runnerTitle + " - " + sessionName, runnerId);
    Disposer.register(this, myContentUI);

    myViewsContentManager = getContentFactory().createContentManager(myContentUI.getContentUI(), true, project);
    myViewsContentManager.addDataProvider((EdtNoGetDataProvider)sink -> {
      sink.set(QuickActionProvider.KEY, myContentUI);
      sink.set(RunnerContentUi.KEY, myContentUI);
    });
    Disposer.register(this, myViewsContentManager);
  }

  @Override
  public @NotNull LayoutViewOptions setTopLeftToolbar(@NotNull ActionGroup actions, @NotNull String place) {
    myContentUI.setTopLeftActions(actions, place);
    return this;
  }

  @Override
  public @NotNull LayoutViewOptions setTopMiddleToolbar(@NotNull ActionGroup actions, @NotNull String place) {
    myContentUI.setTopMiddleActions(actions, place);
    return this;
  }

  @Override
  public @NotNull LayoutViewOptions setTopRightToolbar(@NotNull ActionGroup actions, @NotNull String place) {
    myContentUI.setTopRightActions(actions, place);
    return this;
  }

  @Override
  public @NotNull LayoutStateDefaults initTabDefaults(int id, String text, Icon icon) {
    getLayout().setDefault(id, text, icon);
    return this;
  }

  @Override
  public @NotNull LayoutStateDefaults initContentAttraction(@NotNull String contentId, @NotNull String condition, @NotNull LayoutAttractionPolicy policy) {
    getLayout().setDefaultToFocus(contentId, condition, policy);
    return this;
  }

  @Override
  public @NotNull LayoutStateDefaults cancelContentAttraction(@NotNull String condition) {
    getLayout().cancelDefaultFocusBy(condition);
    return this;
  }

  @Override
  public @NotNull Content addContent(@NotNull Content content) {
    return addContent(content, false, -1, PlaceInGrid.center, false);
  }

  @Override
  public @NotNull Content addContent(@NotNull Content content, int defaultTabId, @NotNull PlaceInGrid defaultPlace, boolean defaultIsMinimized) {
    return addContent(content, true, defaultTabId, defaultPlace, defaultIsMinimized);
  }

  public Content addContent(@NotNull Content content, boolean applyDefaults, int defaultTabId, @NotNull PlaceInGrid defaultPlace, boolean defaultIsMinimized) {
    final String id = content.getUserData(CONTENT_TYPE);

    assert id != null : "Content id is missing, use RunnerLayoutUi to create content instances";

    if (applyDefaults) {
      getLayout().setDefault(id, defaultTabId, defaultPlace, defaultIsMinimized);
    }

    getContentManager().addContent(content);
    return content;
  }

  @Override
  public @NotNull Content createContent(@NotNull String id, @NotNull JComponent component, @NotNull String displayName, @Nullable Icon icon, @Nullable JComponent focusable) {
    return createContent(id, new ComponentWithActions.Impl(component), displayName, icon, focusable);
  }

  @Override
  public @NotNull Content createContent(final @NotNull String contentId, final @NotNull ComponentWithActions withActions, final @NotNull String displayName,
                                        final @Nullable Icon icon,
                                        final @Nullable JComponent toFocus) {
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

  @Override
  public @NotNull JComponent getComponent() {
    return myViewsContentManager.getComponent();
  }

  private static ContentFactory getContentFactory() {
    return ContentFactory.getInstance();
  }

  public RunnerLayout getLayout() {
    return myLayout;
  }

  @Override
  public void updateActionsNow() {
    myContentUI.updateActionsImmediately();
  }

  @Override
  public void beforeTreeDispose() {
    myContentUI.saveUiState();
  }

  @Override
  public void dispose() {
  }

  public @NotNull RunnerContentUi getContentUI() {
    return myContentUI;
  }

  @Override
  public @NotNull ContentManager getContentManager() {
    return myViewsContentManager;
  }

  @Override
  public @NotNull ActionCallback selectAndFocus(final @Nullable Content content, boolean requestFocus, final boolean forced) {
    return selectAndFocus(content, requestFocus, forced, false);
  }

  @Override
  public @NotNull ActionCallback selectAndFocus(final @Nullable Content content, boolean requestFocus, final boolean forced, boolean implicit) {
    if (content == null) return ActionCallback.REJECTED;
    return getContentManager(content).setSelectedContent(content, requestFocus || shouldRequestFocus(), forced, implicit);
  }

  private ContentManager getContentManager(@NotNull Content content) {
    return myContentUI.getContentManager(content);
  }

  private boolean shouldRequestFocus() {
    final Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return focused != null && SwingUtilities.isDescendingFrom(focused, getContentManager().getComponent());
  }

  @Override
  public boolean removeContent(@Nullable Content content, final boolean dispose) {
    return content != null && getContentManager().removeContent(content, dispose);
  }

  @Override
  public boolean isToFocus(final @NotNull Content content, final @NotNull String condition) {
    final String id = content.getUserData(ViewImpl.ID);
    return getLayout().isToFocus(id, condition);
  }

  @Override
  public @NotNull LayoutViewOptions setToFocus(final @Nullable Content content, final @NotNull String condition) {
    getLayout().setToFocus(content != null ? content.getUserData(ViewImpl.ID) : null, condition);
    return this;
  }

  @Override
  public void attractBy(final @NotNull String condition) {
    myContentUI.attractByCondition(condition, true);
  }

  @Override
  public void clearAttractionBy(final @NotNull String condition) {
    myContentUI.clearAttractionByCondition(condition, true);
  }

  public void removeContent(@NotNull String id, final boolean dispose) {
    final Content content = findContent(id);
    if (content != null) {
      getContentManager().removeContent(content, dispose);
    }
  }

  @Override
  public AnAction getLayoutActions() {
    return myContentUI.getLayoutActions();
  }

  @Override
  public AnAction @NotNull [] getLayoutActionsList() {
    final ActionGroup group = (ActionGroup)getLayoutActions();
    return group.getChildren(null);
  }

  @Override
  public @NotNull LayoutViewOptions setTabPopupActions(@NotNull ActionGroup group) {
    myContentUI.setTabPopupActions(group);
    return this;
  }

  @Override
  public @NotNull LayoutViewOptions setLeftToolbar(final @NotNull ActionGroup leftToolbar, final @NotNull String place) {
    myContentUI.setLeftToolbar(leftToolbar, place);
    return this;
  }

  @Override
  public @Nullable Content findContent(final @NotNull String key) {
    return myContentUI.findContent(key);
  }

  @Override
  public @NotNull RunnerLayoutUi addListener(final @NotNull ContentManagerListener listener, final @NotNull Disposable parent) {
    final ContentManager mgr = getContentManager();
    mgr.addContentManagerListener(listener);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        mgr.removeContentManagerListener(listener);
      }
    });
    return this;
  }

  @Override
  public void removeListener(final @NotNull ContentManagerListener listener) {
    getContentManager().removeContentManagerListener(listener);
  }

  @Override
  public void setBouncing(final @NotNull Content content, final boolean activate) {
    myContentUI.processBounce(content, activate);
  }


  @Override
  public boolean isDisposed() {
    return getContentManager().isDisposed();
  }

  @Override
  public @NotNull LayoutViewOptions setMinimizeActionEnabled(final boolean enabled) {
    myContentUI.setMinimizeActionEnabled(enabled);
    return this;
  }

  public LayoutViewOptions setToDisposeRemoveContent(boolean toDispose) {
    myContentUI.setToDisposeRemovedContent(toDispose);
    return this;
  }

  @Override
  public @NotNull LayoutViewOptions setMoveToGridActionEnabled(final boolean enabled) {
    myContentUI.setMovetoGridActionEnabled(enabled);
    return this;
  }

  @Override
  public @NotNull LayoutViewOptions setAttractionPolicy(final @NotNull String contentId, final LayoutAttractionPolicy policy) {
    myContentUI.setPolicy(contentId, policy);
    return this;
  }

  @Override
  public @NotNull LayoutViewOptions setConditionAttractionPolicy(final @NotNull String condition, final LayoutAttractionPolicy policy) {
    myContentUI.setConditionPolicy(condition, policy);
    return this;
  }

  @Override
  public @NotNull LayoutStateDefaults getDefaults() {
    return this;
  }

  @Override
  public @NotNull LayoutViewOptions getOptions() {
    return this;
  }

  @Override
  public @NotNull LayoutViewOptions setAdditionalFocusActions(final @NotNull ActionGroup group) {
    myContentUI.setAdditionalFocusActions(group);
    return this;
  }

  @Override
  public AnAction getSettingsActions() {
    return myContentUI.getSettingsActions();
  }

  @Override
  public AnAction @NotNull [] getSettingsActionsList() {
    final ActionGroup group = (ActionGroup)getSettingsActions();
    return group.getChildren(null);
  }

  @Override
  public Content @NotNull [] getContents() {
    Content[] contents = new Content[getContentManager().getContentCount()];
    for (int i = 0; i < contents.length; i++) {
      contents[i] = getContentManager().getContent(i);
    }
    return contents;
  }

  public void setLeftToolbarVisible(boolean value) {
    myContentUI.setLeftToolbarVisible(value);
  }

  public void setTopLeftActionsBefore(boolean value) {
    myContentUI.setTopLeftActionsBefore(value);
  }

  public void setContentToolbarBefore(boolean value) {
    myContentUI.setContentToolbarBefore(value);
  }

  public void setTopLeftActionsVisible(boolean value) {
    myContentUI.setTopLeftActionsVisible(value);
  }

  public List<AnAction> getActions() {
    return myContentUI.getActions(true);
  }
}
