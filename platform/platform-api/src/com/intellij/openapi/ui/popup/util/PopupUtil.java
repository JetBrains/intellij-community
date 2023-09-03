// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.popup.util;

import com.intellij.codeWithMe.ClientId;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionButtonComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.reference.SoftReference;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.list.SelectablePanel;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class PopupUtil {
  private static final Logger LOG = Logger.getInstance(PopupUtil.class);

  private static final String POPUP_TOGGLE_COMPONENT = "POPUP_TOGGLE_BUTTON";

  private PopupUtil() {
  }

  public static @Nullable Component getOwner(@Nullable Component c) {
    if (c == null) return null;

    final Window wnd = SwingUtilities.getWindowAncestor(c);
    if (wnd instanceof RootPaneContainer) {
      final JRootPane root = ((RootPaneContainer)wnd).getRootPane();
      final JBPopup popup = (JBPopup)root.getClientProperty(JBPopup.KEY);
      if (popup == null) return c;

      final Component owner = popup.getOwner();
      if (owner == null) return c;

      return getOwner(owner);
    }
    else {
      return null;
    }
  }

  public static JBPopup getPopupContainerFor(@Nullable Component c) {
    if (c == null) return null;
    if (c instanceof JComponent) {
      JBPopup popup = (JBPopup)((JComponent)c).getClientProperty(JBPopup.KEY);
      if (popup != null) return popup;
    }
    final Window wnd = SwingUtilities.getWindowAncestor(c);
    if (wnd instanceof JWindow) {
      final JRootPane root = ((JWindow)wnd).getRootPane();
      return (JBPopup)root.getClientProperty(JBPopup.KEY);
    }

    return null;
  }

  public static void setPopupType(final @NotNull PopupFactory factory, final int type) {
    try {
      final Method method = PopupFactory.class.getDeclaredMethod("setPopupType", int.class);
      method.setAccessible(true);
      method.invoke(factory, type);
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  public static int getPopupType(final @NotNull PopupFactory factory) {
    try {
      if (!ClientId.isCurrentlyUnderLocalId()) {
        final Field field = PopupFactory.class.getDeclaredField("HEAVY_WEIGHT_POPUP");
        field.setAccessible(true);
        return (Integer)field.get(null);
      }
      final Method method = PopupFactory.class.getDeclaredMethod("getPopupType");
      method.setAccessible(true);
      final Object result = method.invoke(factory);
      return result instanceof Integer ? (Integer) result : -1;
    }
    catch (Throwable e) {
      LOG.error(e);
    }

    return -1;
  }

  public static Component getActiveComponent() {
    Window[] windows = Window.getWindows();
    for (Window each : windows) {
      if (each.isActive()) {
        return each;
      }
    }

    final IdeFrame frame = IdeFocusManager.findInstance().getLastFocusedFrame();
    if (frame != null) return frame.getComponent();
    return JOptionPane.getRootFrame();
  }

  public static void showBalloonForActiveFrame(final @NotNull @NlsContexts.PopupContent String message, final MessageType type) {
    final Runnable runnable = () -> {
      final IdeFrame frame = IdeFocusManager.findInstance().getLastFocusedFrame();
      if (frame == null) {
        final Project[] projects = ProjectManager.getInstance().getOpenProjects();
        final Project project = projects.length == 0 ? ProjectManager.getInstance().getDefaultProject() : projects[0];
        final JFrame jFrame = WindowManager.getInstance().getFrame(project);
        if (jFrame != null) {
          showBalloonForComponent(jFrame, message, type, true, project);
        } else {
          LOG.info("Can not get component to show message: " + message);
        }
        return;
      }
      showBalloonForComponent(frame.getComponent(), message, type, true, frame.getProject());
    };
    UIUtil.invokeLaterIfNeeded(runnable);
  }

  public static void showBalloonForActiveComponent(final @NotNull @NlsContexts.PopupContent String message, final MessageType type) {
    Runnable runnable = () -> {
      Window[] windows = Window.getWindows();
      Window targetWindow = null;
      for (Window each : windows) {
        if (each.isActive()) {
          targetWindow = each;
          break;
        }
      }

      if (targetWindow == null) {
        targetWindow = JOptionPane.getRootFrame();
      }

      showBalloonForComponent(targetWindow, message, type, true, null);
    };
    UIUtil.invokeLaterIfNeeded(runnable);
  }

  public static void showBalloonForComponent(@NotNull Component component, final @NotNull @NlsContexts.PopupContent String message, final MessageType type,
                                             final boolean atTop, final @Nullable Disposable disposable) {
    final JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    if (popupFactory == null) return;
    BalloonBuilder balloonBuilder = popupFactory.createHtmlTextBalloonBuilder(message, type, null);
    balloonBuilder.setDisposable(disposable == null ? ApplicationManager.getApplication() : disposable);
    Balloon balloon = balloonBuilder.createBalloon();
    Dimension size = component.getSize();
    Balloon.Position position;
    int x;
    int y;
    if (size == null) {
      x = y = 0;
      position = Balloon.Position.above;
    }
    else {
      x = Math.min(10, size.width / 2);
      y = size.height;
      position = Balloon.Position.below;
    }
    balloon.show(new RelativePoint(component, new Point(x, y)), position);
  }

  public static boolean isComboPopupKeyEvent(@NotNull ComponentEvent event, @NotNull JComboBox comboBox) {
    final Component component = event.getComponent();
    if(!comboBox.isPopupVisible() || component == null) return false;
    ComboPopup popup = ReflectionUtil.getField(comboBox.getUI().getClass(), comboBox.getUI(), ComboPopup.class, "popup");
    return popup != null && SwingUtilities.isDescendingFrom(popup.getList(), component);
  }

  public static boolean handleEscKeyEvent() {
    MenuSelectionManager menuSelectionManager = MenuSelectionManager.defaultManager();
    MenuElement[] selectedPath = menuSelectionManager.getSelectedPath();
    if (selectedPath.length > 0) { // hide popup menu if any
      menuSelectionManager.clearSelectedPath();
    }
    else {
      if (ApplicationManager.getApplication() == null) {
        return false;
      }
      final StackingPopupDispatcher popupDispatcher = StackingPopupDispatcher.getInstance();
      if (popupDispatcher != null && !popupDispatcher.isPopupFocused()) {
        return false;
      }
    }
    return true;
  }

  public static void showForActionButtonEvent(@NotNull JBPopup popup, @NotNull AnActionEvent e) {
    InputEvent inputEvent = e.getInputEvent();
    if (inputEvent == null) {
      popup.showInFocusCenter();
    }
    else {
      Component component = inputEvent.getComponent();
      if (component instanceof ActionButtonComponent) {
        popup.showUnderneathOf(component);
      }
      else {
        popup.showInCenterOf(component);
      }
    }
  }

  public static Border createComplexPopupTextFieldBorder() {
    return JBUI.Borders.compound(new JBEmptyBorder(JBUI.CurrentTheme.ComplexPopup.textFieldBorderInsets().getUnscaled()),
                                 JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 0, 0,
                                                         JBUI.CurrentTheme.ComplexPopup.TEXT_FIELD_SEPARATOR_HEIGHT, 0),
                                 new JBEmptyBorder(JBUI.CurrentTheme.ComplexPopup.textFieldInputInsets().getUnscaled()));
  }

  public static void applyNewUIBackground(@Nullable Component component) {
    if (component != null && ExperimentalUI.isNewUI()) {
      component.setBackground(JBUI.CurrentTheme.Popup.BACKGROUND);
    }
  }

  public static Border getComplexPopupHorizontalHeaderBorder() {
    Insets headerInsets = JBUI.CurrentTheme.ComplexPopup.headerInsets().getUnscaled();
    return new JBEmptyBorder(0, headerInsets.left, 0, headerInsets.right);
  }

  public static Border getComplexPopupVerticalHeaderBorder() {
    Insets headerInsets = JBUI.CurrentTheme.ComplexPopup.headerInsets().getUnscaled();
    return new JBEmptyBorder(headerInsets.top, 0, headerInsets.bottom, 0);
  }

  public static void configListRendererFixedHeight(SelectablePanel selectablePanel) {
    configListRendererFlexibleHeight(selectablePanel);
    selectablePanel.setPreferredHeight(JBUI.CurrentTheme.List.rowHeight());
  }

  public static void configListRendererFlexibleHeight(SelectablePanel selectablePanel) {
    int leftRightInset = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.get();
    Insets innerInsets = JBUI.CurrentTheme.Popup.Selection.innerInsets();
    //noinspection UseDPIAwareBorders
    selectablePanel.setBorder(new EmptyBorder(innerInsets.top, innerInsets.left + leftRightInset, innerInsets.bottom,
                              innerInsets.right + leftRightInset));
    selectablePanel.setSelectionArc(JBUI.CurrentTheme.Popup.Selection.ARC.get());
    //noinspection UseDPIAwareInsets
    selectablePanel.setSelectionInsets(new Insets(0, leftRightInset, 0, leftRightInset));
  }

  public static void applyPreviewTitleInsets(JComponent title) {
    if (ExperimentalUI.isNewUI()) {
      title.setBorder(JBUI.Borders.empty(10, 20, 6, 0));
    }
    else {
      title.setBorder(JBUI.Borders.empty(3, 8, 4, 8));
    }
  }

  public static @NotNull Insets getListInsets(boolean titleVisible, boolean adVisible) {
    if (!ExperimentalUI.isNewUI()) {
      return UIUtil.getListViewportPadding(adVisible);
    }

    int topInset = titleVisible ? 0 : JBUI.CurrentTheme.Popup.bodyTopInsetNoHeader();
    int bottomInset = adVisible ? JBUI.CurrentTheme.Popup.bodyBottomInsetBeforeAd() : JBUI.CurrentTheme.Popup.bodyBottomInsetNoAd();
    return new JBInsets(topInset, 0, bottomInset, 0);

  }

  /**
   * In most cases this method is not needed: {@link com.intellij.ui.popup.AbstractPopup} stores the source component automatically.
   *
   * @param toggleComponent treat this component as toggle component and block further mouse event processing
   *                        if user closed the popup by clicking on it
   */
  public static void setPopupToggleComponent(@NotNull JBPopup jbPopup, @Nullable Component toggleComponent) {
    JComponent content = jbPopup.getContent();
    content.putClientProperty(POPUP_TOGGLE_COMPONENT, toggleComponent != null ? new WeakReference<>(toggleComponent) : null);
  }

  public static @Nullable Component getPopupToggleComponent(@NotNull JBPopup jbPopup) {
    return (Component)SoftReference.dereference((WeakReference<?>)jbPopup.getContent().getClientProperty(POPUP_TOGGLE_COMPONENT));
  }

  /**
   * Adds a listener to the popup that will change the toggled state of the Presentation depending on the popup showing state.
   */
  public static void addToggledStateListener(@NotNull JBPopup popup, @NotNull Presentation presentation) {
    popup.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(@NotNull LightweightWindowEvent event) {
        Toggleable.setSelected(presentation, true);
      }

      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        Toggleable.setSelected(presentation, false);
      }
    });
  }
}
