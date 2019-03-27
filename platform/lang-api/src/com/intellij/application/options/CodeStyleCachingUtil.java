// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.openapi.diagnostic.Logger;
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
  private final static Object COMPUTATION_LOCK = new Object();

  @NotNull
  static CodeStyleSettings getCachedCodeStyle(@NotNull PsiFile file) {
    CachedCodeStyleHolder cachedCodeStyleHolder = CachedValuesManager.getCachedValue(file, () -> createHolder(file).getCachedResult());
    return cachedCodeStyleHolder.getCachedSettings();
  }

  private static CachedCodeStyleHolder createHolder(@NotNull PsiFile file) {
    CachedCodeStyleHolder holder = new CachedCodeStyleHolder();
    synchronized (COMPUTATION_LOCK) {
      holder.compute(file);
    }
    if (LOG.isDebugEnabled()) {
      logCached(file, holder);
    }
    return holder;
  }

  static class CachedCodeStyleHolder {
    private @NotNull CodeStyleSettings myCachedSettings = CodeStyle.getDefaultSettings();

    private void compute(@NotNull PsiFile file) {
      //noinspection deprecation
      myCachedSettings = CodeStyleSettingsManager.getInstance(file.getProject()).getCurrentSettings();
      TransientCodeStyleSettings modifiableSettings = new TransientCodeStyleSettings(file, myCachedSettings);
      for (CodeStyleSettingsModifier modifier : CodeStyleSettingsModifier.EP_NAME.getExtensionList()) {
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

    CachedValueProvider.Result<CachedCodeStyleHolder> getCachedResult() {
      return new CachedValueProvider.Result<>(this, this.getDependencies());
    }
  }

  private static void logCached(@NotNull PsiFile file, @NotNull CachedCodeStyleHolder holder) {
    CodeStyleSettings settings = holder.getCachedSettings();
    LOG.debug(String.format(
      "File: %s (%s), cached: %s, tracker: %d", file.getName(), Integer.toHexString(file.hashCode()), settings, settings.getModificationTracker().getModificationCount()));
  }
}
