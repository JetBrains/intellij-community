// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.search;

import com.intellij.DynamicBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.DocumentEvent;
import java.util.List;
import java.util.Set;

public abstract class SearchableOptionsRegistrar{
  public static final @NlsSafe String SETTINGS_GROUP_SEPARATOR = " | ";
  public static final String SEARCHABLE_OPTIONS_XML_NAME = "searchableOptions";

  public static SearchableOptionsRegistrar getInstance() {
    return ApplicationManager.getApplication().getService(SearchableOptionsRegistrar.class);
  }

  public abstract @NotNull ConfigurableHit getConfigurables(@NotNull List<? extends ConfigurableGroup> groups,
                                                            DocumentEvent.EventType type,
                                                            @Nullable Set<? extends Configurable> configurables,
                                                            @NotNull String option,
                                                            @Nullable Project project);

  public abstract @NotNull Set<String> getInnerPaths(SearchableConfigurable configurable, String option);

  /**
   * @deprecated Use {@link SearchableOptionContributor}
   */
  @Deprecated
  public void addOption(@NotNull String option, @Nullable String path, String hit, @NotNull String configurableId, String configurableDisplayName) {
  }

  public abstract boolean isStopWord(String word);

  public abstract @NotNull Set<String> replaceSynonyms(Set<String> options, SearchableConfigurable configurable);

  public abstract @NotNull Set<String> getProcessedWordsWithoutStemming(@NotNull String text);

  public abstract Set<String> getProcessedWords(@NotNull String text);

  public static String getSearchableOptionsXmlName() {
    DynamicBundle.LanguageBundleEP bundle = DynamicBundle.findLanguageBundle();
    return SEARCHABLE_OPTIONS_XML_NAME + (bundle != null ? "_" + bundle.locale : "") + ".xml";
  }
}
