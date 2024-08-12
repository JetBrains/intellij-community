// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.application.options.editor.EditorOptionsProvider;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.postfix.templates.LanguagePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatesUtils;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts.ListItem;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.util.Alarm;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.List;
import java.util.*;

@SuppressWarnings("rawtypes")
public final class PostfixTemplatesConfigurable implements SearchableConfigurable, EditorOptionsProvider, Configurable.NoScroll,
                                                           Configurable.WithEpDependencies {
  public static final Comparator<PostfixTemplate> TEMPLATE_COMPARATOR = Comparator.comparing(PostfixTemplate::getKey);

  private @Nullable PostfixTemplatesCheckboxTree myCheckboxTree;

  private final @NotNull PostfixTemplatesSettings myTemplatesSettings;

  private @Nullable PostfixDescriptionPanel myInnerPostfixDescriptionPanel;

  private final PostfixTemplatesConfigurableUi myUi = new PostfixTemplatesConfigurableUi();
  private final Alarm myUpdateDescriptionPanelAlarm = new Alarm();

  public PostfixTemplatesConfigurable() {
    myTemplatesSettings = PostfixTemplatesSettings.getInstance();

    myUi.postfixTemplatesEnabled.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        updateComponents();
      }
    });
    myUi.shortcutComboBox.addItem(getTab());
    myUi.shortcutComboBox.addItem(getSpace());
    myUi.shortcutComboBox.addItem(getEnter());
    myUi.getDescriptionPanel().setLayout(new BorderLayout());
  }

  @Override
  public @NotNull Collection<BaseExtensionPointName<?>> getDependencies() {
    return Collections.singleton(LanguagePostfixTemplate.EP_NAME);
  }

  private static @NotNull List<PostfixTemplateProvider> getProviders() {
    List<LanguageExtensionPoint> list = LanguagePostfixTemplate.EP_NAME.getExtensionList();
    return ContainerUtil.map(list, el -> (PostfixTemplateProvider)el.getInstance());
  }

  private void createTree() {
    myCheckboxTree = new PostfixTemplatesCheckboxTree() {
      @Override
      protected void selectionChanged() {
        myUpdateDescriptionPanelAlarm.cancelAllRequests();
        myUpdateDescriptionPanelAlarm.addRequest(() -> resetDescriptionPanel(), 100);
      }
    };


    JPanel panel = new JPanel(new BorderLayout());
    boolean canAddTemplate = ContainerUtil.find(getProviders(), p -> StringUtil.isNotEmpty(p.getPresentableName())) != null;

    panel.add(ToolbarDecorator.createDecorator(myCheckboxTree)
                              .setAddActionUpdater(e -> canAddTemplate)
                              .setAddAction(button -> myCheckboxTree.addTemplate(button))
                              .setAddIcon(LayeredIcon.ADD_WITH_DROPDOWN)
                              .setEditActionUpdater(e -> myCheckboxTree.canEditSelectedTemplate())
                              .setEditAction(button -> myCheckboxTree.editSelectedTemplate())
                              .setRemoveActionUpdater(e -> myCheckboxTree.canRemoveSelectedTemplates())
                              .setRemoveAction(button -> myCheckboxTree.removeSelectedTemplates())
                              .addExtraAction(duplicateAction())
                              .createPanel());

    myUi.getTemplatesTreeContainer().setLayout(new BorderLayout());
    myUi.getTemplatesTreeContainer().add(panel);
  }

  private @NotNull AnAction duplicateAction() {
    AnAction button = new AnAction(CodeInsightBundle.messagePointer("action.AnActionButton.text.duplicate"), PlatformIcons.COPY_ICON) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (myCheckboxTree != null) {
          myCheckboxTree.duplicateSelectedTemplate();
        }
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(myCheckboxTree != null && myCheckboxTree.canDuplicateSelectedTemplate());
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }
    };
    button.registerCustomShortcutSet(CommonShortcuts.getDuplicate(), myCheckboxTree, myCheckboxTree);
    return button;
  }

  private void resetDescriptionPanel() {
    if (null != myCheckboxTree && null != myInnerPostfixDescriptionPanel) {
      myInnerPostfixDescriptionPanel.reset(PostfixTemplateMetaData.createMetaData(myCheckboxTree.getSelectedTemplate()));
    }
  }

  @Override
  public @NotNull String getId() {
    return "reference.settingsdialog.IDE.editor.postfix.templates";
  }

  @Override
  public @Nullable String getHelpTopic() {
    return getId();
  }

  @Override
  public @Nls String getDisplayName() {
    return CodeInsightBundle.message("configurable.PostfixTemplatesConfigurable.display.name");
  }

  public @Nullable PostfixTemplatesCheckboxTree getTemplatesTree() {
    return myCheckboxTree;
  }

  @Override
  public @NotNull JComponent createComponent() {
    if (null == myInnerPostfixDescriptionPanel) {
      myInnerPostfixDescriptionPanel = new PostfixDescriptionPanel();
      myUi.getDescriptionPanel().add(myInnerPostfixDescriptionPanel.getComponent());
    }
    if (null == myCheckboxTree) {
      createTree();
    }

    return myUi.getPanel();
  }

  @Override
  public void apply() {
    if (myCheckboxTree != null) {
      myTemplatesSettings.setProviderToDisabledTemplates(myCheckboxTree.getDisabledTemplatesState());
      myTemplatesSettings.setPostfixTemplatesEnabled(myUi.postfixTemplatesEnabled.isSelected());
      myTemplatesSettings.setTemplatesCompletionEnabled(myUi.completionEnabledCheckbox.isSelected());
      myTemplatesSettings.setShortcut(stringToShortcut((String)myUi.shortcutComboBox.getSelectedItem()));

      MultiMap<PostfixTemplateProvider, PostfixTemplate> state = myCheckboxTree.getEditableTemplates();
      for (PostfixTemplateProvider provider : getProviders()) {
        PostfixTemplateStorage.getInstance().setTemplates(provider, state.get(provider));
      }
    }
  }

  @Override
  public void reset() {
    if (myCheckboxTree != null) {
      MultiMap<PostfixTemplateProvider, PostfixTemplate> templatesMap = getProviderToTemplatesMap();

      myCheckboxTree.initTree(templatesMap);
      myCheckboxTree.setDisabledTemplatesState(myTemplatesSettings.getProviderToDisabledTemplates());
      myUi.postfixTemplatesEnabled.setSelected(myTemplatesSettings.isPostfixTemplatesEnabled());
      myUi.completionEnabledCheckbox.setSelected(myTemplatesSettings.isTemplatesCompletionEnabled());
      myUi.shortcutComboBox.setSelectedItem(shortcutToString((char)myTemplatesSettings.getShortcut()));
      resetDescriptionPanel();
      updateComponents();
    }
  }

  private static @NotNull MultiMap<PostfixTemplateProvider, PostfixTemplate> getProviderToTemplatesMap() {
    MultiMap<PostfixTemplateProvider, PostfixTemplate> templatesMap = MultiMap.create();

    for (LanguageExtensionPoint<?> extension : LanguagePostfixTemplate.EP_NAME.getExtensionList()) {
      PostfixTemplateProvider provider = (PostfixTemplateProvider)extension.getInstance();
      Set<PostfixTemplate> templates = PostfixTemplatesUtils.getAvailableTemplates(provider);
      if (!templates.isEmpty()) {
        templatesMap.putValues(provider, ContainerUtil.sorted(templates, TEMPLATE_COMPARATOR));
      }
    }
    return templatesMap;
  }

  @Override
  public boolean isModified() {
    if (myCheckboxTree == null) {
      return false;
    }
    if (myUi.postfixTemplatesEnabled.isSelected() != myTemplatesSettings.isPostfixTemplatesEnabled() ||
        myUi.completionEnabledCheckbox.isSelected() != myTemplatesSettings.isTemplatesCompletionEnabled() ||
        stringToShortcut((String)myUi.shortcutComboBox.getSelectedItem()) != myTemplatesSettings.getShortcut() ||
        !myCheckboxTree.getDisabledTemplatesState().equals(myTemplatesSettings.getProviderToDisabledTemplates())) {
      return true;
    }

    MultiMap<PostfixTemplateProvider, PostfixTemplate> state = myCheckboxTree.getEditableTemplates();
    for (PostfixTemplateProvider provider : getProviders()) {
      if (!PostfixTemplateStorage.getInstance().getTemplates(provider).equals(state.get(provider))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void disposeUIResources() {
    if (myInnerPostfixDescriptionPanel != null) {
      Disposer.dispose(myInnerPostfixDescriptionPanel);
    }
    if (myCheckboxTree != null) {
      Disposer.dispose(myCheckboxTree);
      myCheckboxTree = null;
    }
    Disposer.dispose(myUpdateDescriptionPanelAlarm);
  }

  private void updateComponents() {
    boolean pluginEnabled = myUi.postfixTemplatesEnabled.isSelected();
    myUi.completionEnabledCheckbox.setVisible(!LiveTemplateCompletionContributor.shouldShowAllTemplates());
    myUi.completionEnabledCheckbox.setEnabled(pluginEnabled);
    myUi.shortcutComboBox.setEnabled(pluginEnabled);
    if (myCheckboxTree != null) {
      myCheckboxTree.setEnabled(pluginEnabled);
    }
  }

  private static char stringToShortcut(@Nullable String string) {
    if (getSpace().equals(string)) {
      return TemplateSettings.SPACE_CHAR;
    }
    else if (getEnter().equals(string)) {
      return TemplateSettings.ENTER_CHAR;
    }
    return TemplateSettings.TAB_CHAR;
  }

  private static @ListItem String shortcutToString(char shortcut) {
    if (shortcut == TemplateSettings.SPACE_CHAR) {
      return getSpace();
    }
    if (shortcut == TemplateSettings.ENTER_CHAR) {
      return getEnter();
    }
    return getTab();
  }

  private static @ListItem String getSpace() {
    return CodeInsightBundle.message("template.shortcut.space");
  }

  private static @ListItem String getTab() {
    return CodeInsightBundle.message("template.shortcut.tab");
  }

  private static @ListItem String getEnter() {
    return CodeInsightBundle.message("template.shortcut.enter");
  }
}
