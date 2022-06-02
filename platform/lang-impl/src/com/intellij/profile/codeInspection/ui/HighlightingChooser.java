// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui;

import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.application.options.colors.ColorSettingsUtil;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.EditorTextFieldCellRenderer.RendererComponent;
import com.intellij.ui.EditorTextFieldCellRenderer.SimpleRendererComponent;
import com.intellij.ui.GroupHeaderSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

public abstract class HighlightingChooser extends ComboBoxAction implements DumbAware {
  private HighlightPopup myPopup = null;


  abstract void onKeyChosen(@NotNull TextAttributesKey key);

  public void setChosen(@Nullable TextAttributesKey key) {
    if (key == null) {
      getTemplatePresentation().setText("");
      return;
    }
    final var attributes = ColorSettingsUtil.getErrorTextAttributes();
    String displayName = key.getExternalName();
    for (Pair<TextAttributesKey, @Nls String> pair: attributes) {
      if (key.equals(pair.first)) {
        displayName = pair.second;
        break;
      }
    }
    final String name = stripColorOptionCategory(displayName);
    getTemplatePresentation().setText(name);
  }

  @NotNull
  public static @Nls String stripColorOptionCategory(@NotNull @Nls String displayName) {
    final int separatorPos = displayName.indexOf("//");
    final @Nls String name = separatorPos == -1 ? displayName
                                                : displayName.substring(separatorPos + 2);
    return name;
  }

  @Override
  protected @NotNull DefaultActionGroup createPopupActionGroup(JComponent button) {
    final DefaultActionGroup group = new DefaultActionGroup();

    for (Pair<TextAttributesKey, @Nls String> pair : ColorSettingsUtil.getErrorTextAttributes()) {
      group.add(new HighlightAction(stripColorOptionCategory(pair.second), pair.first, this::onKeyChosen));
    }

    group.addSeparator();

    group.add(new DumbAwareAction(InspectionsBundle.message("inspection.edit.highlighting.action")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        final var dataContext = myPopup == null ? e.getDataContext() : DataManager.getInstance().getDataContext(myPopup.getComponent());
        ColorAndFontOptions.selectOrEditColor(dataContext,
                                              OptionsBundle.message("options.java.attribute.descriptor.error").split("//")[0],
                                              OptionsBundle.message("options.general.display.name"));
      }
    });

    return group;
  }

  @Override
  protected @NotNull ListPopup createActionPopup(@NotNull DataContext context,
                                                 @NotNull JComponent component,
                                                 @Nullable Runnable disposeCallback) {
    final var group = createPopupActionGroup(component);
    myPopup = new HighlightPopup(
      myPopupTitle,
      group,
      context,
      shouldShowDisabledActions(),
      disposeCallback,
      getMaxRows(),
      getPreselectCondition()
    );
    myPopup.setMinimumSize(new Dimension(getMinWidth(), getMinHeight()));
    return myPopup;
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return createComboBoxButton(presentation);
  }
}

class HighlightAction extends DumbAwareAction {
  private final TextAttributesKey myEditorAttributesKey;
  private final Consumer<? super TextAttributesKey> myActionPerformed;

  HighlightAction(@Nls String name, TextAttributesKey attributes, Consumer<? super TextAttributesKey> actionPerformed) {
    super(name);
    myEditorAttributesKey = attributes;
    myActionPerformed = actionPerformed;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myActionPerformed.accept(myEditorAttributesKey);
  }

  public TextAttributesKey getEditorAttributesKey() {
    return myEditorAttributesKey;
  }
}

class HighlightPopup extends PopupFactoryImpl.ActionGroupPopup {

  HighlightPopup(@NlsContexts.PopupTitle @Nullable String title,
                 @NotNull ActionGroup actionGroup,
                 @NotNull DataContext dataContext,
                 boolean showDisabledActions,
                 @Nullable Runnable disposeCallback,
                 int maxRows,
                 Condition<? super AnAction> preselectCondition) {
    super(title, actionGroup, dataContext, false, true, showDisabledActions, false, disposeCallback, maxRows, preselectCondition, null);
  }

  @Override
  protected ListCellRenderer<?> getListElementRenderer() {
    return new HighlightElementRenderer();
  }
}

class HighlightElementRenderer implements ListCellRenderer<PopupFactoryImpl.ActionItem> {

  private final RendererComponent myTextComponent = new SimpleRendererComponent(null, null, true);
  private final JPanel myTextPanel = new NonOpaquePanel();
  private final GroupHeaderSeparator mySeparator = new GroupHeaderSeparator(JBUI.emptyInsets());
  private final JPanel mySeparatorPanel = new NonOpaquePanel();
  private final JBLabel myLabel = new JBLabel();
  private final EditorColorsScheme myColorsScheme;

  HighlightElementRenderer() {
    myTextComponent.getEditor().getContentComponent().setOpaque(false);
    myColorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
    myTextPanel.add(myTextComponent);
    myTextPanel.setBorder(
      new CompoundBorder(new EmptyBorder(JBUI.CurrentTheme.ActionsList.cellPadding()),
                         JBUI.Borders.empty(1, 0)) // Highlighted text visual fix
    );

    final var opaquePanel = new OpaquePanel(new BorderLayout(), JBUI.CurrentTheme.Popup.BACKGROUND);
    opaquePanel.add(mySeparator);
    mySeparatorPanel.add(opaquePanel, BorderLayout.NORTH);
    myLabel.setBorder(
      new CompoundBorder(new EmptyBorder(JBUI.CurrentTheme.ActionsList.cellPadding()),
                         myLabel.getBorder()));
    mySeparatorPanel.add(myLabel, BorderLayout.CENTER);
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends PopupFactoryImpl.ActionItem> list,
                                                PopupFactoryImpl.ActionItem value,
                                                int index,
                                                boolean isSelected,
                                                boolean cellHasFocus) {
    if (value != null) {
      final var action = value.getAction();
      if (action instanceof HighlightAction) {
        final var highlightAction = (HighlightAction)action;
        final var attributes = myColorsScheme.getAttributes(highlightAction.getEditorAttributesKey());
        myTextComponent.setText(action.getTemplateText(), attributes, false);
        myTextComponent.setSize(myTextComponent.getPreferredSize());
        return myTextPanel;
      } else {
        //noinspection DialogTitleCapitalization
        myLabel.setText(action.getTemplateText());
        return mySeparatorPanel;
      }
    }
    return null;
  }
}