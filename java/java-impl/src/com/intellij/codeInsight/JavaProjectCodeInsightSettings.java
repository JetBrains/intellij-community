/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.PatternUtil;
import com.intellij.util.containers.ConcurrentWeakFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author peter
 */
@State(name = "JavaProjectCodeInsightSettings", storages = @Storage("codeInsightSettings.xml"))
public class JavaProjectCodeInsightSettings implements PersistentStateComponent<JavaProjectCodeInsightSettings> {
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private static final ConcurrentWeakFactoryMap<String, Pattern> ourPatterns = new ConcurrentWeakFactoryMap<String, Pattern>() {
    @Nullable
    @Override
    protected Pattern create(String key) {
      return PatternUtil.fromMask(key);
    }
  };

  @Tag("excluded-names")
  @AbstractCollection(surroundWithTag = false, elementTag = "name", elementValueAttribute = "")
  public List<String> excludedNames = ContainerUtil.newArrayList();

  public static JavaProjectCodeInsightSettings getSettings(@NotNull Project project) {
    return ServiceManager.getService(project, JavaProjectCodeInsightSettings.class);
  }

  public boolean isExcluded(@NotNull String name) {
    for (String excluded : excludedNames) {
      if (nameMatches(name, excluded)) {
        return true;
      }
    }
    for (String excluded : CodeInsightSettings.getInstance().EXCLUDED_PACKAGES) {
      if (nameMatches(name, excluded)) {
        return true;
      }
    }

    return false;
  }

  private static boolean nameMatches(@NotNull String name, String excluded) {
    int length = getMatchingLength(name, excluded);
    return length > 0 && (name.length() == length || name.charAt(length) == '.');
  }

  private static int getMatchingLength(@NotNull String name, String excluded) {
    if (name.startsWith(excluded)) {
      return excluded.length();
    }

    if (excluded.indexOf('*') > 0) {
      Matcher matcher = ourPatterns.get(excluded).matcher(name);
      if (matcher.lookingAt()) {
        return matcher.end();
      }
    }

    return -1;
  }

  @Nullable
  @Override
  public JavaProjectCodeInsightSettings getState() {
    return this;
  }

  @Override
  public void loadState(JavaProjectCodeInsightSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @TestOnly
  public static void setExcludedNames(Project project, Disposable parentDisposable, String... excludes) {
    final JavaProjectCodeInsightSettings instance = getSettings(project);
    assert instance.excludedNames.isEmpty();
    instance.excludedNames = Arrays.asList(excludes);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        instance.excludedNames = ContainerUtil.newArrayList();
      }
    });
  }
}
