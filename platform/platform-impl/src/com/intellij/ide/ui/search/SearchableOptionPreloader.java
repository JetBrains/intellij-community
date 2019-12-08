// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.search;

import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author peter
 */
final class SearchableOptionPreloader extends PreloadingActivity {
  @Override
  public void preload(@NotNull ProgressIndicator indicator) {
    final SearchableOptionsRegistrar registrar = SearchableOptionsRegistrar.getInstance();
    final SearchableOptionProcessor processor = new SearchableOptionProcessor() {
      @Override
      public void addOptions(@NotNull String text,
                             @Nullable String path,
                             @Nullable String hit,
                             @NotNull String configurableId,
                             @Nullable String configurableDisplayName,
                             boolean applyStemming) {
        Set<String> words = applyStemming ? registrar.getProcessedWords(text) : registrar.getProcessedWordsWithoutStemming(text);
        registrar.addOptions(words, path, hit, configurableId, configurableDisplayName);
      }
    };

    SearchableOptionContributor.EP_NAME.forEachExtensionSafe(contributor -> contributor.processOptions(processor));
  }
}
