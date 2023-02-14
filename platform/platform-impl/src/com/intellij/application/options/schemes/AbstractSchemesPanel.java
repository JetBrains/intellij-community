// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.schemes;

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.NonTrivialActionGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Base panel for schemes combo box and related actions. When settings change, {@link #updateOnCurrentSettingsChange()} method must be
 * called to reflect the change in schemes panel. The method should be added to settings model listener.
 *
 * @param <T> The actual scheme type.
 * @see AbstractSchemeActions
 * @see SchemesModel
 */
public abstract class AbstractSchemesPanel<T extends Scheme, InfoComponent extends JComponent> extends JPanel {

  private EditableSchemesCombo<T> mySchemesCombo;
  private AbstractSchemeActions<T> myActions;
  private JComponent myToolbar;
  InfoComponent myInfoComponent;
  
  // region Colors (probably should be standard for platform UI)
  
  private static final Color HINT_FOREGROUND = JBColor.GRAY;
  @SuppressWarnings("UseJBColor")
  private static final Color ERROR_MESSAGE_FOREGROUND = Color.RED;

  protected static final int DEFAULT_VGAP = 8;
  private JSeparator mySettingsPanelSeparator;

  // endregion

  AbstractSchemesPanel() {
    this(DEFAULT_VGAP);
  }

  AbstractSchemesPanel(int vGap) {
    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    createUIComponents(vGap);
  }

  private void createUIComponents(int vGap) {
    JPanel controlsPanel = createControlsPanel();
    add(controlsPanel);

    JComponent topComponent = createTopComponent();
    if (topComponent != null) {
      add(topComponent);
    }
    JComponent bottomComponent = createBottomComponent();
    if (bottomComponent != null) {
      add(bottomComponent);
    }
    mySettingsPanelSeparator = new JSeparator();
    add(mySettingsPanelSeparator);
    if (vGap > 0) {
      add(Box.createVerticalGlue());
      add(Box.createRigidArea(new JBDimension(0, vGap)));
    }
  }

  public void setSeparatorVisible(boolean visible) {
    mySettingsPanelSeparator.setVisible(visible);
  }

  protected JComponent createTopComponent() {
    return null;
  }

  @Nullable
  protected JComponent createBottomComponent() {
    return null;
  }

  @NotNull
  private JPanel createControlsPanel() {
    JPanel controlsPanel = new JPanel();
    controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.LINE_AXIS));
    String label = getComboBoxLabel();
    if (label != null) {
      controlsPanel.add(new JLabel(label));
      controlsPanel.add(Box.createRigidArea(new JBDimension(10, 0)));
    }
    myActions = createSchemeActions();
    mySchemesCombo = new EditableSchemesCombo<>(this);
    controlsPanel.add(mySchemesCombo.getComponent());
    ActionToolbar toolbar = createToolbar();
    toolbar.setTargetComponent(mySchemesCombo.getComponent());
    myToolbar = toolbar.getComponent();
    controlsPanel.add(Box.createRigidArea(new JBDimension(4, 0)));
    controlsPanel.add(myToolbar);
    controlsPanel.add(Box.createRigidArea(new JBDimension(9, 0)));
    myInfoComponent = createInfoComponent();
    controlsPanel.add(myInfoComponent);
    controlsPanel.add(Box.createHorizontalGlue());

    mySchemesCombo.getComponent().setMaximumSize(mySchemesCombo.getComponent().getPreferredSize());

    int height = mySchemesCombo.getComponent().getPreferredSize().height;
    controlsPanel.setMaximumSize(new Dimension(controlsPanel.getMaximumSize().width, height));
    return controlsPanel;
  }

  @NotNull
  private ActionToolbar createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new ShowSchemesActionsListAction(myActions));
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("SchemesPanelToolbar", group, true);
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    JComponent toolbarComponent = toolbar.getComponent();
    Dimension maxSize = toolbarComponent.getMaximumSize();
    toolbarComponent.setMaximumSize(JBUI.size(22, maxSize.height));
    toolbarComponent.setBorder(JBUI.Borders.empty(3));
    return toolbar;
  }

  public final JComponent getToolbar() {
    return myToolbar;
  }

  /**
   * Creates schemes actions. Used when panel UI components are created.
   * @return Scheme actions associated with the panel.
   * @see AbstractSchemeActions
   */
  @NotNull
  protected abstract AbstractSchemeActions<T> createSchemeActions();
  
  public final T getSelectedScheme() {
    return mySchemesCombo.getSelectedScheme();
  }
  
  public void selectScheme(@Nullable T scheme) {
    mySchemesCombo.selectScheme(scheme);
  }
  
  public final void resetSchemes(@NotNull Collection<? extends T> schemes) {
    mySchemesCombo.resetSchemes(schemes);
  }
  
  public void disposeUIResources() {
    removeAll();
  }

  final void editCurrentSchemeName(@NotNull BiConsumer<? super T, ? super String> newSchemeNameConsumer) {
    T currentScheme = getSelectedScheme();
    if (currentScheme != null) {
      String currentName = currentScheme.getName();
      mySchemesCombo.startEdit(
        currentName,
        getModel().isProjectScheme(currentScheme),
        newName -> {
          if (!newName.equals(currentName)) {
            newSchemeNameConsumer.accept(currentScheme, newName);
          }
        });
    }
  }

  public final void editNewSchemeName(@NotNull String preferredName, boolean isProjectScheme, @NotNull Consumer<? super String> nameConsumer) {
    String name =
      SchemeNameGenerator.getUniqueName(preferredName, schemeName -> getModel().containsScheme(schemeName, isProjectScheme));
    mySchemesCombo.startEdit(name, isProjectScheme, nameConsumer);
  }

  public final void cancelEdit() {
    mySchemesCombo.cancelEdit();
  }

  public final void showInfo(@NotNull @Nls String message, @NotNull MessageType messageType) {
    myToolbar.setVisible(false);
    showMessage(message, messageType);
  }

  protected abstract void showMessage(@NlsContexts.Label @Nullable String message, @NotNull MessageType messageType);

  final void clearInfo() {
    myToolbar.setVisible(true);
    clearMessage();
  }

  protected abstract void clearMessage();

  @NotNull
  public final AbstractSchemeActions<T> getActions() {
    return myActions;
  }

  @NotNull
  protected abstract InfoComponent createInfoComponent();

  /**
   * @return a string label to place before the combobox or {@code null} if it is not needed
   */
  @Nullable
  protected @NlsContexts.Label String getComboBoxLabel() {
    return getSchemeTypeName() + ":";
  }

  @NotNull
  @Nls
  protected String getSchemeTypeName() {
    return ApplicationBundle.message("editbox.scheme.type.name");
  }

  /**
   * @return Schemes model implementation.
   * @see SchemesModel
   */
  @NotNull
  public abstract SchemesModel<T> getModel();

  /**
   * Must be called when any settings are changed.
   */
  public final void updateOnCurrentSettingsChange() {
    mySchemesCombo.updateSelected();
  }

  /**
   * Returns an indent to calculate a left margin for the scheme name in the combo box.
   * By default, all names are aligned to the left.
   *
   * @param scheme the scheme to calculate its indent
   * @return an indent that shows a nesting level for the specified scheme
   */
  protected int getIndent(@NotNull T scheme) {
    return 0;
  }

  /**
   * @return True if the panel supports project-level schemes along with IDE ones. In this case there will be
   *         additional "Copy to Project" and "Copy to IDE" actions for IDE and project schemes respectively and Project/IDE schemes
   *         separators.
   */
  protected abstract boolean supportsProjectSchemes();

  protected abstract boolean highlightNonDefaultSchemes();

  protected boolean hideDeleteActionIfUnavailable() {
    return true;
  }

  public abstract boolean useBoldForNonRemovableSchemes();

  public void showStatus(@NotNull @NlsContexts.PopupContent String message, @NotNull MessageType messageType) {
    BalloonBuilder balloonBuilder = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(message, messageType.getDefaultIcon(),
                                    messageType.getPopupBackground(), null);
    balloonBuilder.setFadeoutTime(5000);
    final Balloon balloon = balloonBuilder.createBalloon();
    Point pointOnComponent = new Point(myToolbar.getWidth() / 4, myToolbar.getHeight() / 4);
    balloon.show(new RelativePoint(myToolbar, pointOnComponent), Balloon.Position.above);
    Disposer.register(ApplicationManager.getApplication(), balloon);
  }

  private static class ShowSchemesActionsListAction extends NonTrivialActionGroup implements DumbAware {
    private final AbstractSchemeActions<?> mySchemeActions;

    ShowSchemesActionsListAction(AbstractSchemeActions<?> schemeActions) {
      setPopup(true);
      mySchemeActions = schemeActions;
      getTemplatePresentation().setIcon(AllIcons.General.GearPlain);
      getTemplatePresentation().setText(IdeBundle.messagePointer("action.presentation.AbstractSchemesPanel.text"));
      getTemplatePresentation().setDescription(IdeBundle.messagePointer("action.presentation.AbstractSchemesPanel.description"));
      getTemplatePresentation().setPerformGroup(true);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return mySchemeActions.getActions().toArray(EMPTY_ARRAY);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ListPopup popup = JBPopupFactory.getInstance().
        createActionGroupPopup(null, this, e.getDataContext(), true, null, Integer.MAX_VALUE);

      HelpTooltip.setMasterPopup(e.getInputEvent().getComponent(), popup);
      Component component = e.getInputEvent().getComponent();
      if (component instanceof ActionButtonComponent) {
        popup.showUnderneathOf(component);
      }
      else {
        popup.showInCenterOf(component);
      }
    }
  }

  protected static void showMessage(@NlsContexts.Label @Nullable String message,
                                    @NotNull MessageType messageType,
                                    @NotNull JLabel infoComponent) {
    infoComponent.setText(message);
    Color foreground =
      messageType == MessageType.INFO ? HINT_FOREGROUND :
      messageType == MessageType.ERROR ? ERROR_MESSAGE_FOREGROUND :
      messageType.getTitleForeground();
    infoComponent.setForeground(foreground);
  }
}
