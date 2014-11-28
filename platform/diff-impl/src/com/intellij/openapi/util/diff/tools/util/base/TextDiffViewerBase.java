package com.intellij.openapi.util.diff.tools.util.base;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.diff.actions.impl.SetEditorSettingsAction;
import com.intellij.openapi.util.diff.api.DiffTool.DiffContext;
import com.intellij.openapi.util.diff.comparison.ComparisonPolicy;
import com.intellij.openapi.util.diff.requests.ContentDiffRequest;
import com.intellij.openapi.util.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.openapi.util.diff.util.CalledInAwt;
import com.intellij.openapi.util.diff.util.DiffUtil;
import com.intellij.ui.ToggleActionButton;
import com.intellij.util.EditorPopupHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;

public abstract class TextDiffViewerBase extends ListenerDiffViewerBase {
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
    super.onDispose();
    destroyEditorListeners();
  }

  @CalledInAwt
  protected void installEditorListeners() {
    List<? extends EditorEx> editors = getEditors();

    for (EditorEx editor : editors) {
      editor.addEditorMouseListener(myEditorPopupListener);
    }

    if (editors.size() > 1) {
      for (EditorEx editor : editors) {
        editor.addPropertyChangeListener(myFontSizeListener);
      }
    }
  }

  @CalledInAwt
  protected void destroyEditorListeners() {
    List<? extends EditorEx> editors = getEditors();

    for (EditorEx editor : editors) {
      editor.removeEditorMouseListener(myEditorPopupListener);
    }

    if (editors.size() > 1) {
      for (EditorEx editor : editors) {
        editor.removePropertyChangeListener(myFontSizeListener);
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
    return Collections.emptyList();
  }

  //
  // Impl
  //

  @NotNull
  protected TextDiffSettings getTextSettings() {
    return myTextSettings;
  }

  @NotNull
  private static TextDiffSettings initTextSettings(@NotNull DiffContext context) {
    TextDiffSettings settings = context.getUserData(TextDiffSettings.KEY);
    if (settings == null) {
      settings = TextDiffSettings.getSettings();
      context.putUserData(TextDiffSettings.KEY, settings);
    }
    return settings;
  }

  //
  // Helpers
  //

  protected static int getLineCount(@NotNull Document document) {
    return DiffUtil.getLineCount(document);
  }

  private class MyFontSizeListener implements PropertyChangeListener {
    private boolean myDuringUpdate = false;

    public void propertyChange(PropertyChangeEvent evt) {
      if (myDuringUpdate) return;

      if (!EditorEx.PROP_FONT_SIZE.equals(evt.getPropertyName())) return;
      if (evt.getOldValue().equals(evt.getNewValue())) return;
      int fontSize = ((Integer)evt.getNewValue()).intValue();

      for (EditorEx editor : getEditors()) {
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


  protected class MySetEditorSettingsAction extends SetEditorSettingsAction {
    public MySetEditorSettingsAction(@NotNull TextDiffSettings settings) {
      super(settings);
    }

    @NotNull
    @Override
    public List<? extends Editor> getEditors() {
      return TextDiffViewerBase.this.getEditors();
    }
  }

  //
  // Actions
  //

  protected class MyInlineHighlightSettingAction extends ToggleAction implements DumbAware {
    public MyInlineHighlightSettingAction() {
      super("Highlight Inline Differences", null, AllIcons.General.TodoDefault); // TODO: new icon
      setEnabledInModalContext(true);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return getTextSettings().isInlineHighlight();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      if (getTextSettings().isInlineHighlight() == state) return;
      getTextSettings().setInlineHighlight(state);
      rediff();
    }
  }

  protected class MyComparisonPolicySettingAction extends ComboBoxAction implements DumbAware {
    public MyComparisonPolicySettingAction() {
      // TODO: pretty icons ?
      setEnabledInModalContext(true);
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();

      presentation.setText(getTextSettings().getComparisonPolicy().getText());

      presentation.setEnabled(true);
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      DefaultActionGroup group = new DefaultActionGroup();

      for (ComparisonPolicy policy : ComparisonPolicy.values()) {
        group.add(new MyPolicyAction(policy));
      }

      return group;
    }

    private class MyPolicyAction extends AnAction implements DumbAware {
      @NotNull private final ComparisonPolicy myPolicy;

      public MyPolicyAction(@NotNull ComparisonPolicy policy) {
        super(policy.getText());
        setEnabledInModalContext(true);
        myPolicy = policy;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (getTextSettings().getComparisonPolicy() == myPolicy) return;
        getTextSettings().setComparisonPolicy(myPolicy);
        MyComparisonPolicySettingAction.this.update(e);
        rediff();
      }
    }
  }

  protected class MyToggleAutoScrollAction extends ToggleActionButton implements DumbAware {
    public MyToggleAutoScrollAction() {
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

  // TODO: EditorActionUtil.createEditorPopupHandler() ?
  private final class MyEditorMouseListener extends EditorPopupHandler {
    @Override
    public void invokePopup(final EditorMouseEvent event) {
      if (!event.isConsumed() && event.getArea() == EditorMouseEventArea.EDITING_AREA) {
        if (myEditorPopupActions.isEmpty()) return;
        ActionGroup group = new DefaultActionGroup(myEditorPopupActions);
        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_POPUP, group);

        MouseEvent e = event.getMouseEvent();
        final Component c = e.getComponent();
        if (c != null && c.isShowing()) {
          popupMenu.getComponent().show(c, e.getX(), e.getY());
        }
        e.consume();
      }
    }
  }
}
