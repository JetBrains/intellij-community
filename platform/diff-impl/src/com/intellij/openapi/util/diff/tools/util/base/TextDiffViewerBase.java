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
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.diff.actions.impl.SetEditorSettingsAction;
import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffContext;
import com.intellij.openapi.util.diff.requests.ContentDiffRequest;
import com.intellij.openapi.util.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.openapi.util.diff.util.CalledInAwt;
import com.intellij.openapi.util.diff.util.DiffUserDataKeys;
import com.intellij.openapi.util.diff.util.DiffUtil;
import com.intellij.ui.ToggleActionButton;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.EditorPopupHandler;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
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
    destroyEditorListeners();
    super.onDispose();
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
      if (context.getUserData(DiffUserDataKeys.DO_NOT_IGNORE_WHITESPACES) != null) {
        settings.setIgnorePolicy(IgnorePolicy.DEFAULT);
      }
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
    public MySetEditorSettingsAction() {
      super(myTextSettings);
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

  protected class HighlightPolicySettingAction extends ComboBoxAction implements DumbAware {
    @NotNull private final DefaultActionGroup myChildren;

    public HighlightPolicySettingAction() {
      // TODO: pretty icons ?
      setEnabledInModalContext(true);

      // TODO: hide "Do not highlight" in oneside mode
      myChildren = new DefaultActionGroup();
      for (HighlightPolicy policy : HighlightPolicy.values()) {
        myChildren.add(new MyPolicyAction(policy));
      }
    }

    @NotNull
    public DefaultActionGroup getPopupGroup() {
      return myChildren;
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();

      presentation.setText(getTextSettings().getHighlightPolicy().getText());

      presentation.setEnabled(true);
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      return myChildren;
    }

    private class MyPolicyAction extends AnAction implements DumbAware {
      @NotNull private final HighlightPolicy myPolicy;

      public MyPolicyAction(@NotNull HighlightPolicy policy) {
        super(policy.getText());
        setEnabledInModalContext(true);
        myPolicy = policy;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (getTextSettings().getHighlightPolicy() == myPolicy) return;
        getTextSettings().setHighlightPolicy(myPolicy);
        HighlightPolicySettingAction.this.update(e);
        rediff();
      }
    }
  }

  protected class IgnorePolicySettingAction extends ComboBoxAction implements DumbAware {
    @NotNull private final DefaultActionGroup myChildren;

    public IgnorePolicySettingAction() {
      // TODO: pretty icons ?
      setEnabledInModalContext(true);

      myChildren = new DefaultActionGroup();
      for (IgnorePolicy policy : IgnorePolicy.values()) {
        myChildren.add(new MyPolicyAction(policy));
      }
    }

    @NotNull
    public DefaultActionGroup getPopupGroup() {
      return myChildren;
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();

      presentation.setText(getTextSettings().getIgnorePolicy().getText());

      presentation.setEnabled(true);
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      return myChildren;
    }

    private class MyPolicyAction extends AnAction implements DumbAware {
      @NotNull private final IgnorePolicy myPolicy;

      public MyPolicyAction(@NotNull IgnorePolicy policy) {
        super(policy.getText());
        setEnabledInModalContext(true);
        myPolicy = policy;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (getTextSettings().getIgnorePolicy() == myPolicy) return;
        getTextSettings().setIgnorePolicy(myPolicy);
        IgnorePolicySettingAction.this.update(e);
        rediff();
      }
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

  protected class ContextRangeSettingAction extends DumbAwareAction { // TODO: add into 'diff popup'
    public ContextRangeSettingAction() {
      super("Context Lines...", "More/Less Lines...", AllIcons.General.ExpandAll);
      setEnabledInModalContext(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final int[] modes = TextDiffSettings.CONTEXT_RANGE_MODES;
      String[] modeLabels = TextDiffSettings.CONTEXT_RANGE_MODE_LABELS;

      //noinspection UseOfObsoleteCollectionType
      Dictionary<Integer, JLabel> sliderLabels = new Hashtable<Integer, JLabel>();
      for (int i = 0; i < modes.length; i++) {
        sliderLabels.put(i, new JLabel(modeLabels[i]));
      }

      JPanel result = new JPanel(new BorderLayout());
      JLabel label = new JLabel("Context Lines:");
      label.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 0));
      JPanel wrapper = new JPanel(new BorderLayout());
      wrapper.add(label, BorderLayout.NORTH);
      result.add(wrapper, BorderLayout.WEST);
      final JSlider slider = new JSlider(SwingConstants.HORIZONTAL, 0, modes.length - 1, 0);
      slider.setMinorTickSpacing(1);
      slider.setPaintTicks(true);
      slider.setPaintTrack(true);
      slider.setSnapToTicks(true);
      UIUtil.setSliderIsFilled(slider, true);
      slider.setPaintLabels(true);
      slider.setLabelTable(sliderLabels);
      result.add(slider, BorderLayout.CENTER);

      for (int i = 0; i < modes.length; i++) {
        int mark = modes[i];
        if (mark == getTextSettings().getContextRange()) {
          slider.setValue(i);
        }
      }

      JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(result, slider).createPopup();
      popup.setFinalRunnable(new Runnable() {
        @Override
        public void run() {
          int value = slider.getModel().getValue();
          if (getTextSettings().getContextRange() != modes[value]) {
            getTextSettings().setContextRange(modes[value]);
            rediff(); // TODO: we can just update foldings
          }
        }
      });
      if (e.getInputEvent() instanceof MouseEvent) {
        MouseEvent inputEvent = ((MouseEvent)e.getInputEvent());
        int width = result.getPreferredSize().width;
        Point point = new Point(inputEvent.getX() - width / 2, inputEvent.getY());
        RelativePoint absPoint = new RelativePoint(inputEvent.getComponent(), point); // TODO: WTF, wrong component - fix positioning
        popup.show(absPoint);
      }
      else {
        popup.showInBestPositionFor(e.getDataContext());
      }
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
