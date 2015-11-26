/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.ui.search;

import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author peter
 */
public class SearchableOptionPreloader extends PreloadingActivity {
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
        for (String word : words) {
          registrar.addOption(word, path, hit, configurableId, configurableDisplayName);
        }
      }
    };

    for (SearchableOptionContributor contributor : SearchableOptionContributor.EP_NAME.getExtensions()) {
      indicator.checkCanceled();
      contributor.processOptions(processor);
    }
  }
}
