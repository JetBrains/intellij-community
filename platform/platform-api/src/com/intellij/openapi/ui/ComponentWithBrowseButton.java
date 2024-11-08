// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.dsl.builder.DslComponentProperty;
import com.intellij.ui.dsl.builder.VerticalComponentGap;
import com.intellij.ui.dsl.gridLayout.UnscaledGaps;
import com.intellij.ui.dsl.gridLayout.UnscaledGapsKt;
import com.intellij.util.SlowOperations;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.ref.WeakReference;

import static com.intellij.openapi.actionSystem.PlatformDataKeys.UI_DISPOSABLE;

public class ComponentWithBrowseButton<Comp extends JComponent> extends JPanel implements Disposable {
  private final Comp myComponent;
  private final FixedSizeButton myBrowseButton;
  private final ExtendableTextComponent.Extension myInlineButtonExtension;
  private boolean myButtonEnabled = true;

  public ComponentWithBrowseButton(@NotNull Comp component, @Nullable ActionListener browseActionListener) {
    super(new BorderLayout(SystemInfo.isMac || StartupUiUtil.isUnderDarcula() ? 0 : 2, 0));

    myComponent = component;
    // required! otherwise JPanel will occasionally gain focus instead of the component
    setFocusable(false);
    boolean inlineBrowseButton = myComponent instanceof ExtendableTextComponent;
    if (inlineBrowseButton) {
      myInlineButtonExtension = ExtendableTextComponent.Extension.create(
        getDefaultIcon(), getHoveredIcon(), getIconTooltip(), this::notifyActionListeners);
      ((ExtendableTextComponent)myComponent).addExtension(myInlineButtonExtension);
      new DumbAwareAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          notifyActionListeners();
        }
      }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)), myComponent);
    } else {
      myInlineButtonExtension = null;
    }
    add(myComponent, BorderLayout.CENTER);

    myBrowseButton = new FixedSizeButton(myComponent);
    if (isBackgroundSet()) {
      myBrowseButton.setBackground(getBackground());
    }
    if (browseActionListener != null) {
      myBrowseButton.addActionListener(browseActionListener);
    }
    if (!inlineBrowseButton) {
      add(myBrowseButton, BorderLayout.EAST);
    }

    myBrowseButton.setToolTipText(getIconTooltip());
    // FixedSizeButton isn't focusable but it should be selectable via keyboard.
    if (ApplicationManager.getApplication() != null) {  // avoid crash at design time
      new MyDoClickAction(myBrowseButton).registerShortcut(myComponent);
    }
    if (ScreenReader.isActive()) {
      myBrowseButton.setFocusable(true);
      myBrowseButton.getAccessibleContext().setAccessibleName(UIBundle.message("component.with.browse.button.accessible.name"));
    } else if (Registry.is("ide.browse.button.always.focusable", false)) {
      myBrowseButton.setFocusable(true);
    }
    LazyDisposable.installOn(this);

    Insets insets = myComponent.getInsets();
    if (!inlineBrowseButton) {
      insets.right = myBrowseButton.getInsets().right;
    }
    UnscaledGaps visualPaddings = UnscaledGapsKt.toUnscaledGaps(insets);
    putClientProperty(DslComponentProperty.INTERACTIVE_COMPONENT, component);
    putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap.BOTH);
    putClientProperty(DslComponentProperty.VISUAL_PADDINGS, visualPaddings);
  }

  protected @NotNull Icon getDefaultIcon() {
    return AllIcons.General.OpenDisk;
  }

  protected @NotNull Icon getHoveredIcon() {
    return AllIcons.General.OpenDiskHover;
  }

  protected @NotNull @NlsContexts.Tooltip String getIconTooltip() {
    return getTooltip();
  }

  public static @NotNull @NlsContexts.Tooltip String getTooltip() {
    return UIBundle.message("component.with.browse.button.browse.button.tooltip.text") + " (" +
           KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)) + ")";
  }

  private void notifyActionListeners() {
    ActionEvent event = new ActionEvent(myComponent, ActionEvent.ACTION_PERFORMED, "action");
    for (ActionListener listener: myBrowseButton.getActionListeners()) {
      try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
        listener.actionPerformed(event);
      }
    }
  }

  public final @NotNull Comp getChildComponent() {
    return myComponent;
  }

  public void setTextFieldPreferredWidth(final int charCount) {
    JComponent comp = getChildComponent();
    Dimension size = GuiUtils.getSizeByChars(charCount, comp);
    comp.setPreferredSize(size);
    Dimension preferredSize = myBrowseButton.getPreferredSize();
    @SuppressWarnings("removal") boolean keepHeight = UIUtil.isUnderWin10LookAndFeel();
    preferredSize.setSize(size.width + preferredSize.width + 2, keepHeight ? preferredSize.height : preferredSize.height + 2);
    setPreferredSize(preferredSize);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myBrowseButton.setEnabled(enabled && myButtonEnabled);
    myComponent.setEnabled(enabled);
  }

  public void setButtonEnabled(boolean buttonEnabled) {
    myButtonEnabled = buttonEnabled;
    setEnabled(isEnabled());
  }

  public void setButtonVisible(boolean buttonVisible) {
    myBrowseButton.setVisible(buttonVisible);
    if (myInlineButtonExtension != null && myComponent instanceof ExtendableTextComponent) {
      if (buttonVisible) {
        ((ExtendableTextComponent)myComponent).addExtension(myInlineButtonExtension);
      } else {
        ((ExtendableTextComponent)myComponent).removeExtension(myInlineButtonExtension);
      }
    }
  }

  public void setButtonIcon(@NotNull Icon icon) {
    myBrowseButton.setIcon(icon);
    myBrowseButton.setDisabledIcon(IconLoader.getDisabledIcon(icon));
  }

  @Override
  public void setBackground(Color color) {
    super.setBackground(color);
    if (myBrowseButton != null) {
      myBrowseButton.setBackground(color);
    }
  }

  /**
   * Adds specified {@code listener} to the browse button.
   */
  public void addActionListener(ActionListener listener){
    myBrowseButton.addActionListener(listener);
  }

  public void removeActionListener(ActionListener listener) {
    myBrowseButton.removeActionListener(listener);
  }

  public void addBrowseFolderListener(
    @Nullable Project project,
    FileChooserDescriptor fileChooserDescriptor,
    TextComponentAccessor<? super Comp> accessor
  ) {
    addActionListener(new BrowseFolderActionListener<>(this, project, fileChooserDescriptor, accessor));
  }

  /**
   * @deprecated use {@link #addBrowseFolderListener(Project, FileChooserDescriptor, TextComponentAccessor)}
   * together with {@link FileChooserDescriptor#withTitle} and {@link FileChooserDescriptor#withDescription}
   */
  @Deprecated(forRemoval = true)
  public void addBrowseFolderListener(
    @Nullable @NlsContexts.DialogTitle String title,
    @Nullable @NlsContexts.Label String description,
    @Nullable Project project,
    FileChooserDescriptor fileChooserDescriptor,
    TextComponentAccessor<? super Comp> accessor
  ) {
    addBrowseFolderListener(project, fileChooserDescriptor.withTitle(title).withDescription(description), accessor);
  }

  /**
   * @deprecated use {@link #addBrowseFolderListener(Project, FileChooserDescriptor, TextComponentAccessor)}
   * together with {@link FileChooserDescriptor#withTitle} and {@link FileChooserDescriptor#withDescription}
   */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("unused")
  public void addBrowseFolderListener(
    @Nullable @NlsContexts.DialogTitle String title,
    @Nullable @NlsContexts.Label String description,
    @Nullable Project project,
    FileChooserDescriptor fileChooserDescriptor,
    TextComponentAccessor<? super Comp> accessor,
    boolean autoRemoveOnHide
  ) {
    addBrowseFolderListener(title, description, project, fileChooserDescriptor, accessor);
  }

  /** @deprecated use {@link #addActionListener(ActionListener)} instead */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("unused")
  public void addBrowseFolderListener(@Nullable Project project, final BrowseFolderActionListener<Comp> actionListener) {
    addActionListener(actionListener);
  }

  @Override
  public void dispose() {
    ActionListener[] listeners = myBrowseButton.getActionListeners();
    for (ActionListener listener : listeners) {
      myBrowseButton.removeActionListener(listener);
    }
  }

  /**
   * @deprecated The implementation may attach the button via the
   * {@link ExtendableTextComponent#addExtension(ExtendableTextComponent.Extension)}
   * so that the returned button may not be visible to the users
   *
   * @see #setButtonVisible
   * @see #setButtonEnabled
   */
  @Deprecated(forRemoval = true)
  public FixedSizeButton getButton() {
    return myBrowseButton;
  }

  /**
   * Do not use this class directly it is public just to hack other implementation of controls similar to TextFieldWithBrowseButton.
   */
  public static final class MyDoClickAction extends DumbAwareAction {
    private final FixedSizeButton myBrowseButton;
    public MyDoClickAction(FixedSizeButton browseButton) {
      myBrowseButton = browseButton;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myBrowseButton.isVisible() && myBrowseButton.isEnabled());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e){
      myBrowseButton.doClick();
    }

    public void registerShortcut(JComponent textField) {
      ShortcutSet shiftEnter = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK));
      registerCustomShortcutSet(shiftEnter, textField);
      myBrowseButton.setToolTipText(KeymapUtil.getShortcutsText(shiftEnter.getShortcuts()));
    }

    public static void addTo(FixedSizeButton browseButton, JComponent aComponent) {
      new MyDoClickAction(browseButton).registerShortcut(aComponent);
    }
  }

  public static class BrowseFolderActionListener<T extends JComponent> extends BrowseFolderRunnable <T> implements ActionListener {
    public BrowseFolderActionListener(
      @Nullable ComponentWithBrowseButton<T> textField,
      @Nullable Project project,
      FileChooserDescriptor fileChooserDescriptor,
      TextComponentAccessor<? super T> accessor
    ) {
      super(project, fileChooserDescriptor, textField != null ? textField.getChildComponent() : null, accessor);
    }

    /**
     * @deprecated use {@link #BrowseFolderActionListener(ComponentWithBrowseButton, Project, FileChooserDescriptor, TextComponentAccessor)}
     * together with {@link FileChooserDescriptor#withTitle} and {@link FileChooserDescriptor#withDescription}
     */
    @Deprecated(forRemoval = true)
    public BrowseFolderActionListener(
      @Nullable @NlsContexts.DialogTitle String title,
      @Nullable @NlsContexts.Label String description,
      @Nullable ComponentWithBrowseButton<T> textField,
      @Nullable Project project,
      FileChooserDescriptor fileChooserDescriptor,
      TextComponentAccessor<? super T> accessor
    ) {
      this(textField, project, fileChooserDescriptor.withTitle(title).withDescription(description), accessor);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      run();
    }
  }

  @Override
  public final void requestFocus() {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() ->
      IdeFocusManager.getGlobalInstance().requestFocus(myComponent, true));
  }

  @SuppressWarnings("deprecation")
  @Override
  public final void setNextFocusableComponent(Component aComponent) {
    super.setNextFocusableComponent(aComponent);
    myComponent.setNextFocusableComponent(aComponent);
  }

  private KeyEvent myCurrentEvent = null;

  @Override
  protected final boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
    if (condition == WHEN_FOCUSED && myCurrentEvent != e) {
      try {
        myCurrentEvent = e;
        myComponent.dispatchEvent(e);
      }
      finally {
        myCurrentEvent = null;
      }
    }
    if (e.isConsumed()) return true;
    return super.processKeyBinding(ks, e, condition, pressed);
  }

  private static final class LazyDisposable implements Activatable {
    private final WeakReference<ComponentWithBrowseButton<?>> reference;

    private LazyDisposable(ComponentWithBrowseButton<?> component) {
      reference = new WeakReference<>(component);
    }

    private static void installOn(ComponentWithBrowseButton<?> component) {
      LazyDisposable disposable = new LazyDisposable(component);
      UiNotifyConnector.Once.installOn(component, disposable);
    }

    @Override
    public void showNotify() {
      ComponentWithBrowseButton<?> component = reference.get();
      if (component == null) return; // component is collected
      Application app = ApplicationManager.getApplication();
      if (app != null) {
        DataManager dataManager = app.getServiceIfCreated(DataManager.class);
        if (dataManager != null) {
          Disposable disposable = UI_DISPOSABLE.getData(dataManager.getDataContext(component));
          if (disposable != null) {
            Disposer.register(disposable, component);
          }
        }
      }
    }
  }
}
