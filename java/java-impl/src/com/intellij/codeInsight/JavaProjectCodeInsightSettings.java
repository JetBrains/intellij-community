// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.PatternUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@State(name = "JavaProjectCodeInsightSettings", storages = @Storage("codeInsightSettings.xml"))
public class JavaProjectCodeInsightSettings implements PersistentStateComponent<JavaProjectCodeInsightSettings> {
  private static final ConcurrentMap<String, Pattern> ourPatterns = ConcurrentFactoryMap.createWeakMap(PatternUtil::fromMask);

  @XCollection(propertyElementName = "excluded-names", elementName = "name", valueAttributeName = "")
  public List<String> excludedNames = new ArrayList<>();

  public static JavaProjectCodeInsightSettings getSettings(@NotNull Project project) {
    return project.getService(JavaProjectCodeInsightSettings.class);
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

    if (excluded.indexOf('*') >= 0) {
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
  public void loadState(@NotNull JavaProjectCodeInsightSettings state) {
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
        instance.excludedNames = new ArrayList<>();
      }
    });
  }
}
