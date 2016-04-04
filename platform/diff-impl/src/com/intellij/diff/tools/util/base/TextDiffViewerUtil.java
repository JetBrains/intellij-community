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
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.tools.util.FoldingModelSupport;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.icons.AllIcons;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.ui.ToggleActionButton;
import com.intellij.util.EditorPopupHandler;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TextDiffViewerUtil {
  public static final Logger LOG = Logger.getInstance(TextDiffViewerUtil.class);
  public static final Key<Boolean> READ_ONLY_LOCK_KEY = Key.create("ReadOnlyLockAction");

  @NotNull
  public static List<AnAction> createEditorPopupActions() {
    List<AnAction> result = new ArrayList<AnAction>();
    result.add(ActionManager.getInstance().getAction("CompareClipboardWithSelection"));

    result.add(Separator.getInstance());
    ContainerUtil.addAll(result, ((ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_DIFF_EDITOR_POPUP)).getChildren(null));

    return result;
  }

  @NotNull
  public static FoldingModelSupport.Settings getFoldingModelSettings(@NotNull DiffContext context) {
    TextDiffSettings settings = getTextSettings(context);
    return new FoldingModelSupport.Settings(settings.getContextRange(), settings.isExpandByDefault());
  }

  @NotNull
  public static TextDiffSettings getTextSettings(@NotNull DiffContext context) {
    TextDiffSettings settings = context.getUserData(TextDiffSettingsHolder.KEY);
    if (settings == null) {
      settings = TextDiffSettings.getSettings(context.getUserData(DiffUserDataKeys.PLACE));
      context.putUserData(TextDiffSettingsHolder.KEY, settings);
      if (DiffUtil.isUserDataFlagSet(DiffUserDataKeys.DO_NOT_IGNORE_WHITESPACES, context)) {
        settings.setIgnorePolicy(IgnorePolicy.DEFAULT);
      }
    }
    return settings;
  }

  @NotNull
  public static boolean[] checkForceReadOnly(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    int contentCount = request.getContents().size();
    boolean[] result = new boolean[contentCount];

    if (DiffUtil.isUserDataFlagSet(DiffUserDataKeys.FORCE_READ_ONLY, request, context)) {
      Arrays.fill(result, true);
      return result;
    }

    boolean[] data = request.getUserData(DiffUserDataKeys.FORCE_READ_ONLY_CONTENTS);
    if (data != null && data.length == contentCount) {
      return data;
    }

    return result;
  }

  public static void checkDifferentDocuments(@NotNull ContentDiffRequest request) {
    // Actually, this should be a valid case. But it has little practical sense and will require explicit checks everywhere.
    // Some listeners will be processed once instead of 2 times, some listeners will cause illegal document modifications.
    List<DiffContent> contents = request.getContents();

    boolean sameDocuments = false;
    for (int i = 0; i < contents.size(); i++) {
      for (int j = i + 1; j < contents.size(); j++) {
        DiffContent content1 = contents.get(i);
        DiffContent content2 = contents.get(j);
        if (!(content1 instanceof DocumentContent)) continue;
        if (!(content2 instanceof DocumentContent)) continue;
        sameDocuments |= ((DocumentContent)content1).getDocument() == ((DocumentContent)content2).getDocument();
      }
    }

    if (sameDocuments) {
      StringBuilder message = new StringBuilder();
      message.append("DiffRequest with same documents detected\n");
      message.append(request.toString()).append("\n");
      for (DiffContent content : contents) {
        message.append(content.toString()).append("\n");
      }
      LOG.warn(new Throwable(message.toString()));
    }
  }

  //
  // Actions
  //

  // TODO: pretty icons ?
  public static abstract class ComboBoxSettingAction<T> extends ComboBoxAction implements DumbAware {
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

  public static abstract class HighlightPolicySettingAction extends ComboBoxSettingAction<HighlightPolicy> {
    @NotNull protected final TextDiffSettings mySettings;

    public HighlightPolicySettingAction(@NotNull TextDiffSettings settings) {
      mySettings = settings;
    }

    @Override
    protected void applySetting(@NotNull HighlightPolicy setting, @NotNull AnActionEvent e) {
      if (getCurrentSetting() == setting) return;
      UsageTrigger.trigger("diff.TextDiffSettings.HighlightPolicy." + setting.name());
      mySettings.setHighlightPolicy(setting);
      update(e);
      onSettingsChanged();
    }

    @NotNull
    @Override
    protected HighlightPolicy getCurrentSetting() {
      return mySettings.getHighlightPolicy();
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

    protected abstract void onSettingsChanged();
  }

  public static abstract class IgnorePolicySettingAction extends ComboBoxSettingAction<IgnorePolicy> {
    @NotNull protected final TextDiffSettings mySettings;

    public IgnorePolicySettingAction(@NotNull TextDiffSettings settings) {
      mySettings = settings;
    }

    @Override
    protected void applySetting(@NotNull IgnorePolicy setting, @NotNull AnActionEvent e) {
      if (getCurrentSetting() == setting) return;
      UsageTrigger.trigger("diff.TextDiffSettings.IgnorePolicy." + setting.name());
      mySettings.setIgnorePolicy(setting);
      update(e);
      onSettingsChanged();
    }

    @NotNull
    @Override
    protected IgnorePolicy getCurrentSetting() {
      return mySettings.getIgnorePolicy();
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

    protected abstract void onSettingsChanged();
  }

  public static class ToggleAutoScrollAction extends ToggleActionButton implements DumbAware {
    @NotNull protected final TextDiffSettings mySettings;

    public ToggleAutoScrollAction(@NotNull TextDiffSettings settings) {
      super("Synchronize Scrolling", AllIcons.Actions.SynchronizeScrolling);
      mySettings = settings;
      setEnabledInModalContext(true);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySettings.isEnableSyncScroll();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      mySettings.setEnableSyncScroll(state);
    }
  }

  public static abstract class ToggleExpandByDefaultAction extends ToggleActionButton implements DumbAware {
    @NotNull protected final TextDiffSettings mySettings;

    public ToggleExpandByDefaultAction(@NotNull TextDiffSettings settings) {
      super("Collapse unchanged fragments", AllIcons.Actions.Collapseall);
      mySettings = settings;
      setEnabledInModalContext(true);
    }

    @Override
    public boolean isVisible() {
      return mySettings.getContextRange() != -1;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return !mySettings.isExpandByDefault();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      boolean expand = !state;
      if (mySettings.isExpandByDefault() == expand) return;
      mySettings.setExpandByDefault(expand);
      expandAll(expand);
    }

    protected abstract void expandAll(boolean expand);
  }

  public static abstract class ReadOnlyLockAction extends ToggleAction implements DumbAware {
    @NotNull protected final DiffContext myContext;

    public ReadOnlyLockAction(@NotNull DiffContext context) {
      super("Disable editing", null, AllIcons.Nodes.Padlock);
      myContext = context;
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

  public static class EditorReadOnlyLockAction extends ReadOnlyLockAction {
    private final List<? extends EditorEx> myEditableEditors;

    public EditorReadOnlyLockAction(@NotNull DiffContext context, @NotNull List<? extends EditorEx> editableEditors) {
      super(context);
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
  public static List<? extends EditorEx> getEditableEditors(@NotNull List<? extends EditorEx> editors) {
    return ContainerUtil.filter(editors, new Condition<EditorEx>() {
      @Override
      public boolean value(EditorEx editor) {
        return editor != null && !editor.isViewer();
      }
    });
  }

  public static class EditorFontSizeSynchronizer implements PropertyChangeListener {
    @NotNull private final List<? extends EditorEx> myEditors;

    private boolean myDuringUpdate = false;

    public EditorFontSizeSynchronizer(@NotNull List<? extends EditorEx> editors) {
      myEditors = editors;
    }

    public void install(@NotNull Disposable disposable) {
      if (ContainerUtil.skipNulls(myEditors).size() < 2) return;

      for (EditorEx editor : myEditors) {
        if (editor == null) continue;
        editor.addPropertyChangeListener(this, disposable);
      }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      if (myDuringUpdate) return;

      if (!EditorEx.PROP_FONT_SIZE.equals(evt.getPropertyName())) return;
      if (evt.getOldValue().equals(evt.getNewValue())) return;
      int fontSize = ((Integer)evt.getNewValue()).intValue();

      for (EditorEx editor : myEditors) {
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

  public static class EditorActionsPopup extends EditorPopupHandler {
    @NotNull private final List<? extends AnAction> myEditorPopupActions;

    public EditorActionsPopup(@NotNull List<? extends AnAction> editorPopupActions) {
      myEditorPopupActions = editorPopupActions;
    }

    public void install(@NotNull List<? extends EditorEx> editors) {
      for (EditorEx editor : editors) {
        if (editor == null) continue;
        editor.addEditorMouseListener(this);
        editor.setContextMenuGroupId(null); // disabling default context menu
      }
    }

    @Override
    public void invokePopup(final EditorMouseEvent event) {
      if (myEditorPopupActions.isEmpty()) return;
      ActionGroup group = new DefaultActionGroup(myEditorPopupActions);
      EditorPopupHandler handler = EditorActionUtil.createEditorPopupHandler(group);
      handler.invokePopup(event);
    }
  }
}
