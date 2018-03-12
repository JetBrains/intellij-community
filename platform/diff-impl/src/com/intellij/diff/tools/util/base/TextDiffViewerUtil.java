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
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.tools.util.FoldingModelSupport;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.ToggleActionButton;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EditorPopupHandler;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.list;

public class TextDiffViewerUtil {
  private static final Logger LOG = Logger.getInstance(TextDiffViewerUtil.class);

  @NotNull
  public static List<AnAction> createEditorPopupActions() {
    List<AnAction> result = new ArrayList<>();
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
    TextDiffSettings settings = context.getUserData(TextDiffSettings.KEY);
    if (settings == null) {
      settings = TextDiffSettings.getSettings(context.getUserData(DiffUserDataKeys.PLACE));
      context.putUserData(TextDiffSettings.KEY, settings);
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

  public static void installDocumentListeners(@NotNull DocumentListener listener,
                                              @NotNull List<Document> documents,
                                              @NotNull Disposable disposable) {
    for (Document document : ContainerUtil.newHashSet(documents)) {
      document.addDocumentListener(listener, disposable);
    }
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
      LOG.warn(message.toString());
    }
  }

  public static boolean areEqualLineSeparators(@NotNull List<? extends DiffContent> contents) {
    return areEqualDocumentContentProperties(contents, DocumentContent::getLineSeparator);
  }

  public static boolean areEqualCharsets(@NotNull List<? extends DiffContent> contents) {
    boolean sameCharset = areEqualDocumentContentProperties(contents, DocumentContent::getCharset);
    boolean sameBOM = areEqualDocumentContentProperties(contents, DocumentContent::hasBom);
    return sameCharset && sameBOM;
  }

  private static <T> boolean areEqualDocumentContentProperties(@NotNull List<? extends DiffContent> contents,
                                                               @NotNull Function<DocumentContent, T> propertyGetter) {
    List<T> properties = ContainerUtil.mapNotNull(contents, (content) -> {
      if (content instanceof EmptyContent) return null;
      return propertyGetter.fun((DocumentContent)content);
    });

    if (properties.size() < 2) return true;
    return ContainerUtil.newHashSet(properties).size() == 1;
  }

  //
  // Actions
  //

  public static abstract class ComboBoxSettingAction<T> extends ComboBoxAction implements DumbAware {
    private DefaultActionGroup myActions;

    @Override
    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setText(getText(getValue()));
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      return getActions();
    }

    @NotNull
    public DefaultActionGroup getActions() {
      if (myActions == null) {
        myActions = new DefaultActionGroup();
        for (T setting : getAvailableOptions()) {
          myActions.add(new MyAction(setting));
        }
      }
      return myActions;
    }

    @NotNull
    protected abstract List<T> getAvailableOptions();

    @NotNull
    protected abstract T getValue();

    protected abstract void setValue(@NotNull T option);

    @NotNull
    protected abstract String getText(@NotNull T option);

    private class MyAction extends AnAction implements DumbAware {
      @NotNull private final T myOption;

      public MyAction(@NotNull T option) {
        super(getText(option));
        myOption = option;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        setValue(myOption);
      }
    }
  }

  private static abstract class EnumPolicySettingAction<T extends Enum> extends TextDiffViewerUtil.ComboBoxSettingAction<T> {
    @NotNull private final T[] myPolicies;

    public EnumPolicySettingAction(@NotNull T[] policies) {
      assert policies.length > 0;
      myPolicies = policies;
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabledAndVisible(myPolicies.length > 1);
    }

    @NotNull
    @Override
    protected List<T> getAvailableOptions() {
      //noinspection unchecked
      return ContainerUtil.sorted(Arrays.asList(myPolicies));
    }

    @NotNull
    @Override
    public T getValue() {
      T value = getStoredValue();
      if (ArrayUtil.contains(value, myPolicies)) return value;

      List<T> substitutes = getValueSubstitutes(value);
      for (T substitute : substitutes) {
        if (ArrayUtil.contains(substitute, myPolicies)) return substitute;
      }

      return myPolicies[0];
    }

    @NotNull
    protected abstract T getStoredValue();

    @NotNull
    protected abstract List<T> getValueSubstitutes(@NotNull T value);
  }

  public static class HighlightPolicySettingAction extends EnumPolicySettingAction<HighlightPolicy> {
    @NotNull protected final TextDiffSettings mySettings;

    public HighlightPolicySettingAction(@NotNull TextDiffSettings settings,
                                        @NotNull HighlightPolicy... policies) {
      super(policies);
      mySettings = settings;
    }

    @Override
    protected void setValue(@NotNull HighlightPolicy option) {
      if (getValue() == option) return;
      mySettings.setHighlightPolicy(option);
    }

    @NotNull
    @Override
    protected HighlightPolicy getStoredValue() {
      return mySettings.getHighlightPolicy();
    }

    @NotNull
    @Override
    protected List<HighlightPolicy> getValueSubstitutes(@NotNull HighlightPolicy value) {
      if (value == HighlightPolicy.BY_WORD_SPLIT) {
        return list(HighlightPolicy.BY_WORD);
      }
      if (value == HighlightPolicy.DO_NOT_HIGHLIGHT) {
        return list(HighlightPolicy.BY_LINE);
      }
      return list(HighlightPolicy.BY_WORD);
    }

    @NotNull
    @Override
    protected String getText(@NotNull HighlightPolicy option) {
      return option.getText();
    }
  }

  public static class IgnorePolicySettingAction extends EnumPolicySettingAction<IgnorePolicy> {
    @NotNull protected final TextDiffSettings mySettings;

    public IgnorePolicySettingAction(@NotNull TextDiffSettings settings,
                                     @NotNull IgnorePolicy... policies) {
      super(policies);
      mySettings = settings;
    }

    @Override
    protected void setValue(@NotNull IgnorePolicy option) {
      if (getValue() == option) return;
      mySettings.setIgnorePolicy(option);
    }

    @NotNull
    @Override
    protected IgnorePolicy getStoredValue() {
      return mySettings.getIgnorePolicy();
    }

    @NotNull
    @Override
    protected List<IgnorePolicy> getValueSubstitutes(@NotNull IgnorePolicy value) {
      if (value == IgnorePolicy.IGNORE_WHITESPACES_CHUNKS) {
        return list(IgnorePolicy.IGNORE_WHITESPACES);
      }
      if (value == IgnorePolicy.FORMATTING) {
        return list(IgnorePolicy.TRIM_WHITESPACES);
      }
      return list(IgnorePolicy.DEFAULT);
    }

    @NotNull
    @Override
    protected String getText(@NotNull IgnorePolicy option) {
      return option.getText();
    }
  }

  public static class ToggleAutoScrollAction extends ToggleActionButton implements DumbAware {
    @NotNull protected final TextDiffSettings mySettings;

    public ToggleAutoScrollAction(@NotNull TextDiffSettings settings) {
      super("Synchronize Scrolling", AllIcons.Actions.SynchronizeScrolling);
      mySettings = settings;
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
    @NotNull protected final TextDiffSettings mySettings;

    public ReadOnlyLockAction(@NotNull DiffContext context) {
      super("Disable editing", null, AllIcons.Nodes.Padlock);
      myContext = context;
      mySettings = getTextSettings(context);
    }

    protected void applyDefaults() {
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
      return mySettings.isReadOnlyLock();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      mySettings.setReadOnlyLock(state);
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
      applyDefaults();
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
    return ContainerUtil.filter(editors, editor -> !editor.isViewer());
  }

  public static class EditorFontSizeSynchronizer implements PropertyChangeListener {
    @NotNull private final List<? extends EditorEx> myEditors;

    private boolean myDuringUpdate = false;

    public EditorFontSizeSynchronizer(@NotNull List<? extends EditorEx> editors) {
      myEditors = editors;
    }

    public void install(@NotNull Disposable disposable) {
      if (myEditors.size() < 2) return;
      for (EditorEx editor : myEditors) {
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
        if (evt.getSource() != editor) updateEditor(editor, fontSize);
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
