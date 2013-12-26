/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.diagnostic;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Allows to apply & persist custom log debug categories which can be turned on by user via the {@link com.intellij.ide.actions.DebugLogConfigureAction}. <br/>
 * Applies these custom categories on startup.
 */
public class DebugLogManager extends ApplicationComponent.Adapter {

  private static final Logger LOG = Logger.getInstance(DebugLogManager.class);
  private static final String LOG_DEBUG_CATEGORIES = "log.debug.categories";

  @Override
  public void initComponent() {
    List<String> categories = getSavedCategories();
    if (categories.isEmpty()) {
      saveCategories(getCurrentCategories());
    }
    else {
      applyCategories(categories);
    }
  }

  @NotNull
  public List<String> getSavedCategories() {
    String value = PropertiesComponent.getInstance().getValue(LOG_DEBUG_CATEGORIES);
    return value == null ? Collections.<String>emptyList() : fromString(value);
  }

  public void applyCategories(@NotNull List<String> categories) {
    for (String category : categories) {
      org.apache.log4j.Logger logger = LogManager.getLogger(category);
      if (logger != null) {
        logger.setLevel(Level.DEBUG);
      }
    }
    LOG.info("Set DEBUG for the following categories: " + categories);
  }

  public void saveCategories(@NotNull List<String> categories) {
    PropertiesComponent.getInstance().setValue(LOG_DEBUG_CATEGORIES, toString(categories));
  }

  @NotNull
  private static List<String> fromString(@NotNull String text) {
    return Arrays.asList(StringUtil.splitByLines(text, true));
  }

  @NotNull
  private static String toString(@NotNull List<String> categories) {
    return StringUtil.join(categories, "\n");
  }

  @NotNull
  private static List<String> getCurrentCategories() {
    Enumeration currentLoggers = LogManager.getCurrentLoggers();
    return ContainerUtil.mapNotNull(ContainerUtil.toList(currentLoggers), new Function<Object, String>() {
      @Override
      public String fun(Object o) {
        if (o instanceof org.apache.log4j.Logger) {
          String category = ((org.apache.log4j.Logger)o).getName();
          if (Logger.getInstance(category).isDebugEnabled()) {
            return category;
          }
        }
        return null;
      }
    });
  }

}
