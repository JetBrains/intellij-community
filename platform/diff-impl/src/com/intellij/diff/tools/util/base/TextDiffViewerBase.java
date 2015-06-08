/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.util.base;

import com.intellij.diff.DiffContext;
import com.intellij.diff.actions.impl.SetEditorSettingsAction;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.tools.util.FoldingModelSupport;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.ui.ToggleActionButton;
import com.intellij.util.EditorPopupHandler;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class TextDiffViewerBase extends ListenerDiffViewerBase {
  public static final Key<Boolean> READ_ONLY_LOCK_KEY = Key.create("ReadOnlyLockAction");

  @NotNull private final TextDiffSettings myTextSettings;

  @NotNull private final MyFontSizeListener myFontSizeListener = new MyFontSizeListener();

  @NotNull private final MyEditorMouseListener myEditorPopupListener = new MyEditorMouseListener();
  @NotNull private List<AnAction> myEditorPopupActions;

  public TextDiffViewerBase(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request);

    myTextSettings = initTextSettings(context);
  }

  @Override
  protected void onInit() {
    super.onInit();
    myEditorPopupActions = createEditorPopupActions();
    installEditorListeners();
  }

  @Override
  protected void onDispose() {
    destroyEditorListeners();
    super.onDispose();
  }

  @CalledInAwt
  protected void installEditorListeners() {
    List<? extends EditorEx> editors = getEditors();

    for (EditorEx editor : editors) {
      if (editor != null) editor.addEditorMouseListener(myEditorPopupListener);
    }

    if (editors.size() > 1) {
      for (EditorEx editor : editors) {
        if (editor != null) editor.addPropertyChangeListener(myFontSizeListener);
      }
    }
  }

  @CalledInAwt
  protected void destroyEditorListeners() {
    List<? extends EditorEx> editors = getEditors();

    for (EditorEx editor : editors) {
      if (editor != null) editor.removeEditorMouseListener(myEditorPopupListener);
    }

    if (editors.size() > 1) {
      for (EditorEx editor : editors) {
        if (editor != null) editor.removePropertyChangeListener(myFontSizeListener);
      }
    }
  }

  //
  // Abstract
  //

  @NotNull
  protected abstract List<? extends EditorEx> getEditors();

  @NotNull
  protected List<AnAction> createEditorPopupActions() {
    List<AnAction> result = new ArrayList<AnAction>();
    result.add(ActionManager.getInstance().getAction("CompareClipboardWithSelection"));

    result.add(Separator.getInstance());
    ContainerUtil.addAll(result, ((ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_DIFF_EDITOR_POPUP)).getChildren(null));

    return result;
  }

  @CalledInAwt
  protected void onSettingsChanged() {
    rediff();
  }

  //
  // Impl
  //

  @NotNull
  protected TextDiffSettings getTextSettings() {
    return myTextSettings;
  }

  @NotNull
  protected FoldingModelSupport.Settings getFoldingModelSettings() {
    TextDiffSettings settings = getTextSettings();
    return new FoldingModelSupport.Settings(settings.getContextRange(), settings.isExpandByDefault());
  }

  @NotNull
  private static TextDiffSettings initTextSettings(@NotNull DiffContext context) {
    TextDiffSettings settings = context.getUserData(TextDiffSettingsHolder.KEY);
    if (settings == null) {
      settings = TextDiffSettings.getSettings(context.getUserData(DiffUserDataKeysEx.PLACE));
      context.putUserData(TextDiffSettingsHolder.KEY, settings);
      if (DiffUtil.isUserDataFlagSet(DiffUserDataKeys.DO_NOT_IGNORE_WHITESPACES, context)) {
        settings.setIgnorePolicy(IgnorePolicy.DEFAULT);
      }
    }
    return settings;
  }

  //
  // Helpers
  //

  @NotNull
  protected boolean[] checkForceReadOnly() {
    int contentCount = myRequest.getContents().size();
    boolean[] result = new boolean[contentCount];

    if (DiffUtil.isUserDataFlagSet(DiffUserDataKeys.FORCE_READ_ONLY, myRequest, myContext)) {
      Arrays.fill(result, true);
      return result;
    }

    boolean[] data = myRequest.getUserData(DiffUserDataKeys.FORCE_READ_ONLY_CONTENTS);
    if (data != null && data.length == contentCount) {
      return data;
    }

    return result;
  }

  private class MyFontSizeListener implements PropertyChangeListener {
    private boolean myDuringUpdate = false;

    public void propertyChange(PropertyChangeEvent evt) {
      if (myDuringUpdate) return;

      if (!EditorEx.PROP_FONT_SIZE.equals(evt.getPropertyName())) return;
      if (evt.getOldValue().equals(evt.getNewValue())) return;
      int fontSize = ((Integer)evt.getNewValue()).intValue();

      for (EditorEx editor : getEditors()) {
        if (editor != null && evt.getSource() != editor) updateEditor(editor, fontSize);
      }
    }

    public void updateEditor(@NotNull EditorEx editor, int fontSize) {
      try {
        myDuringUpdate = true;
        editor.setFontSize(fontSize);
      }
      finally {
        myDuringUpdate = false;
      }
    }
  }

  protected class MySetEditorSettingsAction extends SetEditorSettingsAction {
    public MySetEditorSettingsAction() {
      super(myTextSettings, getEditors());
    }
  }

  //
  // Actions
  //

  // TODO: pretty icons ?
  protected static abstract class ComboBoxSettingAction<T> extends ComboBoxAction implements DumbAware {
    private DefaultActionGroup myChildren;

    public ComboBoxSettingAction() {
      setEnabledInModalContext(true);
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setText(getText(getCurrentSetting()));
    }

    @NotNull
    public DefaultActionGroup getPopupGroup() {
      initChildren();
      return myChildren;
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      initChildren();
      return myChildren;
    }

    private void initChildren() {
      if (myChildren == null) {
        myChildren = new DefaultActionGroup();
        for (T setting : getAvailableSettings()) {
          myChildren.add(new MyAction(setting));
        }
      }
    }

    @NotNull
    protected abstract List<T> getAvailableSettings();

    @NotNull
    protected abstract String getText(@NotNull T setting);

    @NotNull
    protected abstract T getCurrentSetting();

    protected abstract void applySetting(@NotNull T setting, @NotNull AnActionEvent e);

    private class MyAction extends AnAction implements DumbAware {
      @NotNull private final T mySetting;

      public MyAction(@NotNull T setting) {
        super(getText(setting));
        setEnabledInModalContext(true);
        mySetting = setting;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        applySetting(mySetting, e);
      }
    }
  }

  protected class HighlightPolicySettingAction extends ComboBoxSettingAction<HighlightPolicy> {
    public HighlightPolicySettingAction() {
    }

    @Override
    protected void applySetting(@NotNull HighlightPolicy setting, @NotNull AnActionEvent e) {
      if (getCurrentSetting() == setting) return;
      getTextSettings().setHighlightPolicy(setting);
      update(e);
      onSettingsChanged();
    }

    @NotNull
    @Override
    protected HighlightPolicy getCurrentSetting() {
      return getTextSettings().getHighlightPolicy();
    }

    @NotNull
    @Override
    protected String getText(@NotNull HighlightPolicy setting) {
      return setting.getText();
    }

    @NotNull
    @Override
    protected List<HighlightPolicy> getAvailableSettings() {
      return Arrays.asList(HighlightPolicy.values());
    }
  }

  protected class IgnorePolicySettingAction extends ComboBoxSettingAction<IgnorePolicy> {
    public IgnorePolicySettingAction() {
    }

    @Override
    protected void applySetting(@NotNull IgnorePolicy setting, @NotNull AnActionEvent e) {
      if (getCurrentSetting() == setting) return;
      getTextSettings().setIgnorePolicy(setting);
      update(e);
      onSettingsChanged();
    }

    @NotNull
    @Override
    protected IgnorePolicy getCurrentSetting() {
      return getTextSettings().getIgnorePolicy();
    }

    @NotNull
    @Override
    protected String getText(@NotNull IgnorePolicy setting) {
      return setting.getText();
    }

    @NotNull
    @Override
    protected List<IgnorePolicy> getAvailableSettings() {
      return Arrays.asList(IgnorePolicy.values());
    }
  }

  protected class ToggleAutoScrollAction extends ToggleActionButton implements DumbAware {
    public ToggleAutoScrollAction() {
      super("Synchronize Scrolling", AllIcons.Actions.SynchronizeScrolling);
      setEnabledInModalContext(true);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return getTextSettings().isEnableSyncScroll();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      getTextSettings().setEnableSyncScroll(state);
    }
  }

  protected abstract class ToggleExpandByDefaultAction extends ToggleActionButton implements DumbAware {
    public ToggleExpandByDefaultAction() {
      super("Collapse unchanged fragments", AllIcons.Actions.Collapseall);
      setEnabledInModalContext(true);
    }

    @Override
    public boolean isVisible() {
      return getTextSettings().getContextRange() != -1;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return !getTextSettings().isExpandByDefault();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      boolean expand = !state;
      if (getTextSettings().isExpandByDefault() == expand) return;
      getTextSettings().setExpandByDefault(expand);
      expandAll(expand);
    }

    protected abstract void expandAll(boolean expand);
  }

  protected abstract class ReadOnlyLockAction extends ToggleAction implements DumbAware {
    public ReadOnlyLockAction() {
      super("Disable editing", null, AllIcons.Nodes.Padlock);
      setEnabledInModalContext(true);
    }

    protected void init() {
      if (isVisible()) { // apply default state
        setSelected(null, isSelected(null));
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (!isVisible()) {
        e.getPresentation().setEnabledAndVisible(false);
      }
      else {
        super.update(e);
      }
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myContext.getUserData(READ_ONLY_LOCK_KEY) != Boolean.FALSE;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myContext.putUserData(READ_ONLY_LOCK_KEY, state);
      doApply(state);
    }

    private boolean isVisible() {
      return myContext.getUserData(DiffUserDataKeysEx.SHOW_READ_ONLY_LOCK) == Boolean.TRUE && canEdit();
    }

    protected abstract void doApply(boolean readOnly);

    protected abstract boolean canEdit();
  }

  protected class EditorReadOnlyLockAction extends ReadOnlyLockAction {
    private final List<? extends EditorEx> myEditableEditors;

    public EditorReadOnlyLockAction() {
      this(getEditableEditors(getEditors()));
    }

    public EditorReadOnlyLockAction(@NotNull List<? extends EditorEx> editableEditors) {
      myEditableEditors = editableEditors;
      init();
    }

    @Override
    protected void doApply(boolean readOnly) {
      for (EditorEx editor : myEditableEditors) {
        editor.setViewer(readOnly);
      }
    }

    @Override
    protected boolean canEdit() {
      return !myEditableEditors.isEmpty();
    }
  }

  @NotNull
  protected static List<? extends EditorEx> getEditableEditors(@NotNull List<? extends EditorEx> editors) {
    return ContainerUtil.filter(editors, new Condition<EditorEx>() {
      @Override
      public boolean value(EditorEx editor) {
        return editor != null && !editor.isViewer();
      }
    });
  }

  private final class MyEditorMouseListener extends EditorPopupHandler {
    @Override
    public void invokePopup(final EditorMouseEvent event) {
      if (myEditorPopupActions.isEmpty()) return;
      ActionGroup group = new DefaultActionGroup(myEditorPopupActions);
      EditorPopupHandler handler = EditorActionUtil.createEditorPopupHandler(group);
      handler.invokePopup(event);
    }
  }
}
