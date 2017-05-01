/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.application.options.schemes;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
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
  protected InfoComponent myInfoComponent;
  
  // region Colors (probably should be standard for platform UI)
  
  protected static final Color HINT_FOREGROUND = JBColor.GRAY;
  @SuppressWarnings("UseJBColor")
  protected static final Color ERROR_MESSAGE_FOREGROUND = Color.RED;
  
  // endregion

  public AbstractSchemesPanel() {
    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    createUIComponents();
  }
  
  private void createUIComponents() {
    JPanel controlsPanel = new JPanel();
    controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.LINE_AXIS));
    controlsPanel.add(new JLabel(getSchemeTypeName() + ":"));
    controlsPanel.add(Box.createRigidArea(new JBDimension(10, 0)));
    myActions = createSchemeActions();
    mySchemesCombo = new EditableSchemesCombo<>(this);
    controlsPanel.add(mySchemesCombo.getComponent());
    myToolbar = createToolbar();
    controlsPanel.add(Box.createRigidArea(new JBDimension(6, 0)));
    controlsPanel.add(myToolbar);
    controlsPanel.add(Box.createRigidArea(new JBDimension(9, 0)));
    myInfoComponent = createInfoComponent();
    controlsPanel.add(myInfoComponent);
    controlsPanel.add(Box.createHorizontalGlue());
    controlsPanel.setMaximumSize(new Dimension(controlsPanel.getMaximumSize().width, mySchemesCombo.getComponent().getPreferredSize().height));
    add(controlsPanel);
    add(Box.createRigidArea(new JBDimension(0, 12)));
    add(new JSeparator());
    add(Box.createVerticalGlue());
    add(Box.createRigidArea(new JBDimension(0, 10)));
  }
  
  private JComponent createToolbar() {
    DefaultActionGroup toolbarActionGroup = new DefaultActionGroup();
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.NAVIGATION_BAR_TOOLBAR, toolbarActionGroup, true);
    JComponent toolbarComponent = toolbar.getComponent();
    toolbarActionGroup.add(new ShowSchemesActionsListAction(myActions.getActions(), toolbarComponent));
    toolbarComponent.setMaximumSize(new Dimension(toolbarComponent.getPreferredSize().width, Short.MAX_VALUE));
    return toolbarComponent;
  }

  public final JComponent getToolbar() {
    return myToolbar;
  }

  /**
   * Creates schemes actions. Used when panel UI components are created.
   * @return Scheme actions associated with the panel.
   * @see AbstractSchemeActions
   */
  protected abstract AbstractSchemeActions<T> createSchemeActions();
  
  public final T getSelectedScheme() {
    return mySchemesCombo.getSelectedScheme();
  }
  
  public void selectScheme(@Nullable T scheme) {
    mySchemesCombo.selectScheme(scheme);
  }
  
  public final void resetSchemes(@NotNull Collection<T> schemes) {
    mySchemesCombo.resetSchemes(schemes);
  }
  
  public void disposeUIResources() {
    removeAll();
  }

  public final void editCurrentSchemeName(@NotNull BiConsumer<T,String> newSchemeNameConsumer) {
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

  public final void editNewSchemeName(@NotNull String preferredName, boolean isProjectScheme, @NotNull Consumer<String> nameConsumer) {
    String name =
      SchemeNameGenerator.getUniqueName(preferredName, schemeName -> getModel().containsScheme(schemeName, isProjectScheme));
    mySchemesCombo.startEdit(name, isProjectScheme, nameConsumer);
  }

  public final void cancelEdit() {
    mySchemesCombo.cancelEdit();
  }

  public final void showInfo(@Nullable String message, @NotNull MessageType messageType) {
    myToolbar.setVisible(false);
    showMessage(message, messageType);
  }

  protected abstract void showMessage(@Nullable String message, @NotNull MessageType messageType);

  public final void clearInfo() {
    myToolbar.setVisible(true);
    clearMessage();
  }

  protected abstract void clearMessage();

  public final AbstractSchemeActions<T> getActions() {
    return myActions;
  }

  @NotNull
  protected abstract InfoComponent createInfoComponent();

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
   * @return True if the panel supports project-level schemes along with IDE ones. In this case there will be
   *         additional "Copy to Project" and "Copy to IDE" actions for IDE and project schemes respectively and Project/IDE schemes
   *         separators.
   */
  protected abstract boolean supportsProjectSchemes();

  protected abstract boolean highlightNonDefaultSchemes();

  public abstract boolean useBoldForNonRemovableSchemes();

  public void showStatus(final String message, MessageType messageType) {
    BalloonBuilder balloonBuilder = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(message, messageType.getDefaultIcon(),
                                    messageType.getPopupBackground(), null);
    balloonBuilder.setFadeoutTime(5000);
    final Balloon balloon = balloonBuilder.createBalloon();
    Point pointOnComponent = new Point(myToolbar.getWidth() / 4, myToolbar.getHeight() / 4);
    balloon.show(new RelativePoint(myToolbar, pointOnComponent), Balloon.Position.above);
    Disposer.register(ProjectManager.getInstance().getDefaultProject(), balloon);
  }

  private static class ShowSchemesActionsListAction extends DumbAwareAction {

    private final ActionGroup myActionGroup;
    private final Component myParentComponent;

    ShowSchemesActionsListAction(Collection<AnAction> actions, Component component) {
      myParentComponent = component;
      myActionGroup = new DefaultActionGroup(actions.toArray(new AnAction[actions.size()]));
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      p.setIcon(AllIcons.General.Gear);
      p.setEnabledAndVisible(isEnabledAndVisible(e));
    }

    private boolean isEnabledAndVisible(AnActionEvent e) {
      // copy existing event because an action update changes its presentation
      e = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, e.getDataContext());
      for (AnAction action : myActionGroup.getChildren(e)) {
        action.update(e); // ensure that at least action is enabled and visible
        if (e.getPresentation().isEnabledAndVisible()) return true;
      }
      return false;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final ListPopup actionGroupPopup =
        JBPopupFactory.getInstance().createActionGroupPopup(null, myActionGroup, e.getDataContext(), true, null, Integer.MAX_VALUE);
      actionGroupPopup.show(new RelativePoint(myParentComponent, new Point(JBUI.scale(2), myParentComponent.getHeight() - JBUI.scale(1))));
    }
  }

  protected static void showMessage(@Nullable String message,
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
