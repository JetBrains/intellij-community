// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.application.options.editor.EditorOptionsProvider;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.postfix.templates.LanguagePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatesUtils;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.JComponent;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesConfigurableUiKt.createAsyncSettingsInitPlaceholder;

@ApiStatus.Internal
@SuppressWarnings("rawtypes")
public final class PostfixTemplatesConfigurable implements SearchableConfigurable, EditorOptionsProvider, Configurable.NoScroll,
                                                           Configurable.WithEpDependencies {

  public static final Comparator<PostfixTemplate> TEMPLATE_COMPARATOR = Comparator.comparing(PostfixTemplate::getKey);

  private @Nullable Disposable myAsyncLoaderDisposable;
  private @Nullable PostfixTemplatesSettings myTemplatesSettings;
  private @Nullable PostfixTemplatesConfigurableUi myUi;

  @Override
  public @NotNull Collection<BaseExtensionPointName<?>> getDependencies() {
    return Collections.singleton(LanguagePostfixTemplate.EP_NAME);
  }

  static @Unmodifiable @NotNull List<PostfixTemplateProvider> getProviders() {
    List<LanguageExtensionPoint> list = LanguagePostfixTemplate.EP_NAME.getExtensionList();
    return ContainerUtil.map(list, el -> (PostfixTemplateProvider)el.getInstance());
  }

  @Override
  public @NotNull String getId() {
    return "reference.settingsdialog.IDE.editor.postfix.templates";
  }

  @Override
  public @NotNull String getHelpTopic() {
    return getId();
  }

  @Override
  public @Nls String getDisplayName() {
    return CodeInsightBundle.message("configurable.PostfixTemplatesConfigurable.display.name");
  }

  public @Nullable PostfixTemplatesCheckboxTree getTemplatesTree() {
    return myUi == null ? null : myUi.checkboxTree;
  }

  @Override
  public @NotNull JComponent createComponent() {
    myAsyncLoaderDisposable = Disposer.newDisposable();

    return createAsyncSettingsInitPlaceholder(
      settings -> {
        myTemplatesSettings = settings;
        myUi = new PostfixTemplatesConfigurableUi();
        reset();

        return myUi.panel;
      },
      myAsyncLoaderDisposable
    );
  }

  @Override
  public void apply() {
    if (myUi != null && myTemplatesSettings != null) {
      myTemplatesSettings.setProviderToDisabledTemplates(myUi.checkboxTree.getDisabledTemplatesState());
      myTemplatesSettings.setPostfixTemplatesEnabled(myUi.postfixTemplatesEnabled.isSelected());
      myTemplatesSettings.setTemplatesCompletionEnabled(myUi.completionEnabledCheckbox.isSelected());
      myTemplatesSettings.setShowAsSeparateGroup(myUi.postfixTemplatesGroupCompletion.isSelected());
      myTemplatesSettings.setShortcut(myUi.getSelectedShortcut());

      MultiMap<PostfixTemplateProvider, PostfixTemplate> state = myUi.checkboxTree.getEditableTemplates();
      for (PostfixTemplateProvider provider : getProviders()) {
        PostfixTemplateStorage.getInstance().setTemplates(provider, state.get(provider));
      }
    }
  }

  @Override
  public void reset() {
    if (myUi != null && myTemplatesSettings != null) {
      MultiMap<PostfixTemplateProvider, PostfixTemplate> templatesMap = getProviderToTemplatesMap();

      myUi.checkboxTree.initTree(templatesMap);
      myUi.checkboxTree.setDisabledTemplatesState(myTemplatesSettings.getProviderToDisabledTemplates());
      myUi.postfixTemplatesEnabled.setSelected(myTemplatesSettings.isPostfixTemplatesEnabled());
      myUi.completionEnabledCheckbox.setSelected(myTemplatesSettings.isTemplatesCompletionEnabled());
      myUi.postfixTemplatesGroupCompletion.setSelected(myTemplatesSettings.isShowAsSeparateGroup());
      myUi.shortcutComboBox.setSelectedItem((char)myTemplatesSettings.getShortcut());
      myUi.resetDescriptionPanel();
      myUi.updateComponents();
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
    if (myUi == null || myTemplatesSettings == null) {
      return false;
    }

    if (myUi.postfixTemplatesEnabled.isSelected() != myTemplatesSettings.isPostfixTemplatesEnabled() ||
        myUi.completionEnabledCheckbox.isSelected() != myTemplatesSettings.isTemplatesCompletionEnabled() ||
        myUi.postfixTemplatesGroupCompletion.isSelected() != myTemplatesSettings.isShowAsSeparateGroup() ||
        myUi.getSelectedShortcut() != myTemplatesSettings.getShortcut() ||
        !myUi.checkboxTree.getDisabledTemplatesState().equals(myTemplatesSettings.getProviderToDisabledTemplates())) {
      return true;
    }

    MultiMap<PostfixTemplateProvider, PostfixTemplate> state = myUi.checkboxTree.getEditableTemplates();
    for (PostfixTemplateProvider provider : getProviders()) {
      if (!PostfixTemplateStorage.getInstance().getTemplates(provider).equals(state.get(provider))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void disposeUIResources() {
    if (myAsyncLoaderDisposable != null) {
      Disposer.dispose(myAsyncLoaderDisposable);
      myAsyncLoaderDisposable = null;
    }

    if (myUi != null) {
      Disposer.dispose(myUi);
      myUi = null;
      myTemplatesSettings = null;
    }
  }
}
