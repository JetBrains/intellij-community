// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;

class CodeStyleCachingUtil {
  private final static Logger LOG = Logger.getInstance(CodeStyleCachingUtil.class);

  private final static ExtensionPointName<CodeStyleSettingsModifier> CODE_STYLE_SETTINGS_MODIFIER_EP_NAME =
    ExtensionPointName.create("com.intellij.codeStyleSettingsModifier");

  @NotNull
  static CodeStyleSettings getCachedCodeStyle(@NotNull PsiFile file) {
    CachedCodeStyleHolder cachedCodeStyleHolder = CachedValuesManager.getCachedValue(
      file,
      () -> {
        CachedCodeStyleHolder holder = new CachedCodeStyleHolder(file);
        if (LOG.isDebugEnabled()) {
          logCached(file, holder);
        }
        return new CachedValueProvider.Result<>(holder, holder.getDependencies());
      });
    return cachedCodeStyleHolder.getCachedSettings();
  }

  static class CachedCodeStyleHolder {
    private @NotNull CodeStyleSettings myCachedSettings;

    CachedCodeStyleHolder(@NotNull PsiFile file) {
      //noinspection deprecation
      myCachedSettings = CodeStyleSettingsManager.getInstance(file.getProject()).getCurrentSettings();
      updateFor(file);
    }

    private void updateFor(@NotNull PsiFile file) {
      TransientCodeStyleSettings modifiableSettings = new TransientCodeStyleSettings(file, myCachedSettings);
      for (CodeStyleSettingsModifier modifier : CODE_STYLE_SETTINGS_MODIFIER_EP_NAME.getExtensionList()) {
        if (modifier.modifySettings(modifiableSettings, file)) {
          LOG.debug("Modifier: " + modifier.getClass().getName());
          modifiableSettings.setModifier(modifier);
          myCachedSettings = modifiableSettings;
          break;
        }
      }
    }

    @NotNull
    Object[] getDependencies() {
      return myCachedSettings instanceof TransientCodeStyleSettings ?
             ((TransientCodeStyleSettings)myCachedSettings).getDependencies().toArray() :
             new Object[]{myCachedSettings.getModificationTracker()};
    }

    @NotNull
    CodeStyleSettings getCachedSettings() {
      return myCachedSettings;
    }
  }

  private static void logCached(@NotNull PsiFile file, @NotNull CachedCodeStyleHolder holder) {
    CodeStyleSettings settings = holder.getCachedSettings();
    LOG.debug(String.format(
      "File: %s, cached: %s, tracker: %d", file.getName(), settings, settings.getModificationTracker().getModificationCount()));
  }
}
