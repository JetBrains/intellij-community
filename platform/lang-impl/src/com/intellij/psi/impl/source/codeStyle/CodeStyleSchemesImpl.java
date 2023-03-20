// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.application.options.schemes.SchemeNameGenerator;
import com.intellij.configurationStore.LazySchemeProcessor;
import com.intellij.configurationStore.SchemeDataHolder;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.psi.codeStyle.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public abstract class CodeStyleSchemesImpl extends CodeStyleSchemes {
  @NonNls
  public static final String CODE_STYLES_DIR_PATH = "codestyles";

  protected final SchemeManager<CodeStyleScheme> mySchemeManager;

  public CodeStyleSchemesImpl(@NotNull SchemeManagerFactory schemeManagerFactory) {
    mySchemeManager = schemeManagerFactory.create(CODE_STYLES_DIR_PATH, new LazySchemeProcessor<CodeStyleScheme, CodeStyleSchemeImpl>() {
      @NotNull
      @Override
      public CodeStyleSchemeImpl createScheme(@NotNull SchemeDataHolder<? super CodeStyleSchemeImpl> dataHolder,
                                              @NotNull String name,
                                              @NotNull Function<? super String, String> attributeProvider,
                                              boolean isBundled) {
        return new CodeStyleSchemeImpl(attributeProvider.apply("name"), attributeProvider.apply("parent"), dataHolder);
      }
    }, null, null, SettingsCategory.CODE);

    mySchemeManager.loadSchemes();
    setCurrentScheme(getDefaultScheme());

    FileTypeIndentOptionsProvider.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull FileTypeIndentOptionsProvider extension,
                                 @NotNull PluginDescriptor pluginDescriptor) {
        CodeStyleSettingsManager.getInstance()
          .registerFileTypeIndentOptions(getAllSettings(), extension.getFileType(), extension.createIndentOptions());
      }

      @Override
      public void extensionRemoved(@NotNull FileTypeIndentOptionsProvider extension,
                                   @NotNull PluginDescriptor pluginDescriptor) {
        CodeStyleSettingsManager.getInstance()
          .unregisterFileTypeIndentOptions(getAllSettings(), extension.getFileType());
      }
    }, null);

    LanguageCodeStyleSettingsProvider.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull LanguageCodeStyleSettingsProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        CodeStyleSettingsManager codeStyleSettingsManager = CodeStyleSettingsManager.getInstance();
        if (codeStyleSettingsManager != null) {
          codeStyleSettingsManager.registerLanguageSettings(getAllSettings(), extension);
          codeStyleSettingsManager.registerCustomSettings(getAllSettings(), extension);
        }
      }

      @Override
      public void extensionRemoved(@NotNull LanguageCodeStyleSettingsProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
       CodeStyleSettingsManager codeStyleSettingsManager = CodeStyleSettingsManager.getInstance();
        if (codeStyleSettingsManager != null) {
          codeStyleSettingsManager.unregisterLanguageSettings(getAllSettings(), extension);
          codeStyleSettingsManager.unregisterCustomSettings(getAllSettings(), extension);
        }
      }
    }, null);
    CodeStyleSettingsProvider.EXTENSION_POINT_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull CodeStyleSettingsProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        CodeStyleSettingsManager codeStyleSettingsManager = CodeStyleSettingsManager.getInstance();
        if (codeStyleSettingsManager != null) {
          codeStyleSettingsManager.registerCustomSettings(getAllSettings(), extension);
        }
      }

      @Override
      public void extensionRemoved(@NotNull CodeStyleSettingsProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        CodeStyleSettingsManager codeStyleSettingsManager = CodeStyleSettingsManager.getInstance();
        if (codeStyleSettingsManager != null) {
          codeStyleSettingsManager.unregisterCustomSettings(getAllSettings(), extension);
        }
      }
    }, null);
  }

  @Override
  public List<CodeStyleScheme> getAllSchemes() {
    return mySchemeManager.getAllSchemes();
  }

  private List<CodeStyleSettings> getAllSettings() {
    return ContainerUtil.map(mySchemeManager.getAllSchemes(), scheme -> scheme.getCodeStyleSettings());
  }

  @Override
  @Transient
  public CodeStyleScheme getCurrentScheme() {
    return mySchemeManager.getActiveScheme();
  }

  @Override
  public void setCurrentScheme(CodeStyleScheme scheme) {
    mySchemeManager.setCurrent(scheme);
  }

  @Override
  public CodeStyleScheme createNewScheme(String preferredName, CodeStyleScheme parentScheme) {
    return new CodeStyleSchemeImpl(
      SchemeNameGenerator.getUniqueName(preferredName, parentScheme, name -> mySchemeManager.findSchemeByName(name) != null),
      false,
      parentScheme);
  }

  @Override
  public void deleteScheme(@NotNull CodeStyleScheme scheme) {
    if (scheme.isDefault()) {
      throw new IllegalArgumentException("Unable to delete default scheme!");
    }

    CodeStyleSchemeImpl currentScheme = (CodeStyleSchemeImpl)getCurrentScheme();
    if (currentScheme == scheme) {
      CodeStyleScheme newCurrentScheme = getDefaultScheme();
      if (newCurrentScheme == null) {
        throw new IllegalStateException("Unable to load default scheme!");
      }
      setCurrentScheme(newCurrentScheme);
    }
    mySchemeManager.removeScheme(scheme);
  }

  @Override
  public CodeStyleScheme getDefaultScheme() {
    CodeStyleScheme defaultScheme = mySchemeManager.findSchemeByName(CodeStyleScheme.DEFAULT_SCHEME_NAME);
    if (defaultScheme == null) {
      defaultScheme = new CodeStyleSchemeImpl(CodeStyleScheme.DEFAULT_SCHEME_NAME, true, null);
      addScheme(defaultScheme);
    }
    return defaultScheme;
  }

  @Nullable
  @Override
  public CodeStyleScheme findSchemeByName(@NotNull String name) {
    return mySchemeManager.findSchemeByName(name);
  }

  @Override
  public void addScheme(@NotNull CodeStyleScheme scheme) {
    mySchemeManager.addScheme(scheme);
  }

  @NotNull
  public static SchemeManager<CodeStyleScheme> getSchemeManager() {
    return ((CodeStyleSchemesImpl)CodeStyleSchemes.getInstance()).mySchemeManager;
  }
}
