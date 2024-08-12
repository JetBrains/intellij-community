// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui;

import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.application.options.colors.ColorSettingsUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.ide.DataManager;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
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
import com.intellij.ui.popup.ActionPopupOptions;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class HighlightingChooser extends ComboBoxAction implements DumbAware {
  public static final Map<TextAttributesKey, Supplier<@Nls String>> ATTRIBUTES_CUSTOM_NAMES = new HashMap<>();

  static {
    ATTRIBUTES_CUSTOM_NAMES.put(CodeInsightColors.INFORMATION_ATTRIBUTES,
                                InspectionsBundle.messagePointer("inspection.no.highlighting"));
    ATTRIBUTES_CUSTOM_NAMES.put(CodeInsightColors.CONSIDERATION_ATTRIBUTES,
                                InspectionsBundle.messagePointer("inspection.choose.highlighting"));
  }

  private HighlightPopup myPopup = null;
  private final SeverityRegistrar mySeverityRegistrar;

  public HighlightingChooser(@NotNull SeverityRegistrar severityRegistrar) {
    mySeverityRegistrar = severityRegistrar;
  }

  abstract void onKeyChosen(@NotNull TextAttributesKey key);

  public void setChosen(@NotNull TextAttributesKey key) {
    if (ATTRIBUTES_CUSTOM_NAMES.containsKey(key)) {
      getTemplatePresentation().setText(ATTRIBUTES_CUSTOM_NAMES.get(key));
      return;
    }

    final var attributes = ColorSettingsUtil.getErrorTextAttributes();
    String displayName = key.getExternalName();
    for (Pair<TextAttributesKey, @Nls String> pair: attributes) {
      if (key.toString().equals(pair.first.toString())) {
        displayName = pair.second;
        break;
      }
    }
    final String name = stripColorOptionCategory(displayName);
    getTemplatePresentation().setText(name);
  }

  public static @NotNull @Nls String stripColorOptionCategory(@NotNull @Nls String displayName) {
    final int separatorPos = displayName.indexOf("//");
    final @Nls String name = separatorPos == -1 ? displayName
                                                : displayName.substring(separatorPos + 2);
    return name;
  }

  @Override
  protected @NotNull DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
    final DefaultActionGroup group = new DefaultActionGroup();
    final EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();

    for (Pair<TextAttributesKey, @Nls String> pair : ColorSettingsUtil.getErrorTextAttributes()) {
      group.add(new HighlightAction(stripColorOptionCategory(pair.second), pair.first, scheme.getAttributes(pair.first), this::onKeyChosen));
    }

    final Collection<HighlightInfoType> standardSeverities = SeverityRegistrar.standardSeverities();
    for (HighlightSeverity severity : mySeverityRegistrar.getAllSeverities()) {
      final var highlightInfoType = mySeverityRegistrar.getHighlightInfoTypeBySeverity(severity);
      if (standardSeverities.contains(highlightInfoType)) continue;
      final TextAttributesKey attributes = mySeverityRegistrar.getHighlightInfoTypeBySeverity(severity).getAttributesKey();
      group.add(new HighlightAction(severity.getDisplayName(), attributes, mySeverityRegistrar.getTextAttributesBySeverity(severity), this::onKeyChosen));
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
    final var group = createPopupActionGroup(component, context);
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
    final ComboBoxButton button = createComboBoxButton(presentation);
    button.setMinimumSize(new Dimension(100, button.getPreferredSize().height));
    button.setPreferredSize(button.getMinimumSize());
    return button;
  }
}

final class HighlightAction extends DumbAwareAction {
  private final TextAttributesKey myEditorAttributesKey;
  private final TextAttributes myTextAttributes;
  private final Consumer<? super TextAttributesKey> myActionPerformed;

  HighlightAction(@Nls String name, TextAttributesKey textAttributesKey, TextAttributes textAttributes, Consumer<? super TextAttributesKey> actionPerformed) {
    super(name);
    myEditorAttributesKey = textAttributesKey;
    myTextAttributes = textAttributes;
    myActionPerformed = actionPerformed;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myActionPerformed.accept(myEditorAttributesKey);
  }

  public TextAttributesKey getEditorAttributesKey() {
    return myEditorAttributesKey;
  }

  TextAttributes getTextAttributes() {
    return myTextAttributes;
  }
}

final class HighlightPopup extends PopupFactoryImpl.ActionGroupPopup {

  HighlightPopup(@NlsContexts.PopupTitle @Nullable String title,
                 @NotNull ActionGroup actionGroup,
                 @NotNull DataContext dataContext,
                 boolean showDisabledActions,
                 @Nullable Runnable disposeCallback,
                 int maxRows,
                 Condition<? super AnAction> preselectCondition) {
    super(null, title, actionGroup, dataContext, ActionPlaces.POPUP, new PresentationFactory(),
          ActionPopupOptions.create(false, true, showDisabledActions, false, maxRows, false, preselectCondition),
          disposeCallback);
  }

  @Override
  protected ListCellRenderer<?> getListElementRenderer() {
    return new HighlightElementRenderer();
  }
}

final class HighlightElementRenderer implements ListCellRenderer<PopupFactoryImpl.ActionItem> {

  private final RendererComponent myTextComponent = new SimpleRendererComponent(null, null, true);
  private final JPanel myTextPanel = new NonOpaquePanel();
  private final JPanel mySeparatorPanel = new NonOpaquePanel();
  private final JBLabel myLabel = new JBLabel();

  HighlightElementRenderer() {
    myTextComponent.getEditor().getContentComponent().setOpaque(false);
    myTextPanel.add(myTextComponent);
    myTextPanel.setBorder(
      new CompoundBorder(new EmptyBorder(JBUI.CurrentTheme.ActionsList.cellPadding()),
                         JBUI.Borders.empty(1, 0)) // Highlighted text visual fix
    );

    final var opaquePanel = new OpaquePanel(new BorderLayout(), JBUI.CurrentTheme.Popup.BACKGROUND);
    final GroupHeaderSeparator separator = new GroupHeaderSeparator(JBUI.emptyInsets());
    opaquePanel.add(separator);
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
      if (action instanceof HighlightAction highlightAction) {
        final TextAttributes attributes = highlightAction.getTextAttributes();
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