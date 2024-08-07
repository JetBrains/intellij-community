// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.concurrency.annotations.RequiresBlockingContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.DocumentEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public abstract class SearchableOptionsRegistrar {
  public static final @NlsSafe String SETTINGS_GROUP_SEPARATOR = " | ";
  public static final String SEARCHABLE_OPTIONS_XML_NAME = "searchableOptions";

  @RequiresBlockingContext
  public static SearchableOptionsRegistrar getInstance() {
    return ApplicationManager.getApplication().getService(SearchableOptionsRegistrar.class);
  }

  @ApiStatus.Internal
  public abstract @NotNull ConfigurableHit getConfigurables(@NotNull List<? extends ConfigurableGroup> groups,
                                                            DocumentEvent.EventType type,
                                                            @Nullable Set<? extends Configurable> configurables,
                                                            @NotNull String option,
                                                            @Nullable Project project);

  public abstract @NotNull Set<@NotNull String> getInnerPaths(SearchableConfigurable configurable, String option);

  /**
   * @deprecated Use {@link SearchableOptionContributor}
   */
  @SuppressWarnings("unused")
  @Deprecated
  public void addOption(@NotNull String option, @Nullable String path, String hit, @NotNull String configurableId, String configurableDisplayName) {
  }

  public abstract boolean isStopWord(String word);

  public abstract @NotNull Set<String> replaceSynonyms(Set<String> options, SearchableConfigurable configurable);

  public abstract @NotNull Set<String> getProcessedWordsWithoutStemming(@NotNull String text);

  public abstract Set<String> getProcessedWords(@NotNull String text);

  public interface AdditionalLocationProvider {
    /**
     * Returns the additional location for {@code searchableOptions.xml}.
     * By default, {@link SearchableOptionsRegistrar} will look for {@code searchableOptions.xml} inside plugin by path
     * {@code <plugin-jar>/search/<prefix>.searchableOptions.<bundle>.xml}. Path returned by this method will also be
     * checked for additional {@code <prefix>.searchableOptions.<bundle>.xml} files to load.
     *
     * @return the directory to check for additional searchable options files
     */
    @Nullable Path getAdditionalLocation();
  }
}
