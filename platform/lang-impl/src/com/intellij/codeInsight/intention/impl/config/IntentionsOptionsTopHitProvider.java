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
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey.Malenkov
 */
public class IntentionsOptionsTopHitProvider extends OptionsTopHitProvider {
  @Override
  public String getId() {
    return "intentions";
  }

  @NotNull
  @Override
  public Collection<BooleanOptionDescription> getOptions(@Nullable Project project) {
    IntentionManagerSettings settings = IntentionManagerSettings.getInstance();
    if (settings == null) {
      return Collections.emptyList();
    }

    Collection<BooleanOptionDescription> options = new ArrayList<>();
    for (IntentionActionMetaData data : settings.getMetaData()) {
      options.add(new Option(settings, data));
    }
    return Collections.unmodifiableCollection(options);
  }

  private static final class Option extends BooleanOptionDescription {
    private final IntentionManagerSettings mySettings;
    private final IntentionActionMetaData myMetaData;

    private Option(IntentionManagerSettings settings, IntentionActionMetaData data) {
      super(getOptionName(data), IntentionSettingsConfigurable.HELP_ID);
      mySettings = settings;
      myMetaData = data;
    }

    @Override
    public boolean isOptionEnabled() {
      return mySettings.isEnabled(myMetaData);
    }

    @Override
    public void setOptionState(boolean enabled) {
      mySettings.setEnabled(myMetaData, enabled);
    }

    private static String getOptionName(IntentionActionMetaData data) {
      StringBuilder sb = new StringBuilder();
      for (String category : data.myCategory) {
        sb.append(category).append(": ");
      }
      return sb.append(data.getFamily()).toString();
    }
  }
}
