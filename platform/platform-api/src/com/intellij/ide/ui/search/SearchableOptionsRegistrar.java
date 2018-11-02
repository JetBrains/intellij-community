// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.ui.search;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.DocumentEvent;
import java.util.Map;
import java.util.Set;

public abstract class SearchableOptionsRegistrar{
  public static final String SEARCHABLE_OPTIONS_XML = "searchableOptions.xml";

  public static SearchableOptionsRegistrar getInstance(){
    return ServiceManager.getService(SearchableOptionsRegistrar.class);
  }

  @NotNull
  public abstract ConfigurableHit getConfigurables(final ConfigurableGroup[] groups,
                                                     final DocumentEvent.EventType type,
                                                     final Set<? extends Configurable> configurables,
                                                     final String option,
                                                     final Project project);

  @Nullable
  public abstract String getInnerPath(SearchableConfigurable configurable, String option);

  public abstract void addOption(String option, String path, String hit, final String configurableId, final String configurableDisplayName);

  public abstract boolean isStopWord(String word);

  public abstract Set<String> getSynonym(final String option, @NotNull final SearchableConfigurable configurable);

  public abstract Set<String> replaceSynonyms(Set<String> options, SearchableConfigurable configurable);

  public abstract Map<String, Set<String>> findPossibleExtension(@NotNull String prefix, final Project project);


  public abstract Set<String> getProcessedWordsWithoutStemming(@NotNull String text);

  public abstract Set<String> getProcessedWords(@NotNull String text);

}
