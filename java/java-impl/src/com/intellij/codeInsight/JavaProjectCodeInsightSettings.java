// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.options.OptionControllerProvider;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
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
public class JavaProjectCodeInsightSettings implements PersistentStateComponent<JavaProjectCodeInsightSettings>, OptionContainer {
  private static final ConcurrentMap<String, Pattern> ourPatterns = ConcurrentFactoryMap.createWeakMap(PatternUtil::fromMask);

  @XCollection(propertyElementName = "excluded-names", elementName = "name", valueAttributeName = "")
  public List<String> excludedNames = new ArrayList<>();
  @XCollection(propertyElementName = "included-static-names", elementName = "name", valueAttributeName = "")
  public List<String> includedAutoStaticNames = new ArrayList<>();

  public static JavaProjectCodeInsightSettings getSettings(@NotNull Project project) {
    return project.getService(JavaProjectCodeInsightSettings.class);
  }

  public List<String> getAllIncludedAutoStaticNames() {
    List<String> names = new ArrayList<>(includedAutoStaticNames);
    names.addAll(JavaIdeCodeInsightSettings.getInstance().includedAutoStaticNames);
    return names;
  }

  /**
   * Determines whether the given name should be considered as a static auto-import name.
   * Can be a fully qualified name of a class or member of a class
   *
   * @param name the name to check, which can be null
   * @return true if the name is included in the list of static auto-import names, false otherwise
   */
  public boolean isStaticAutoImportName(@Nullable String name) {
    if (name == null) return false;
    List<String> names = getAllIncludedAutoStaticNames();
    return names.contains(name) || names.contains(StringUtil.getPackageName(name));
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

  @Override
  public @Nullable JavaProjectCodeInsightSettings getState() {
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

  @Override
  public @NotNull OptionController getOptionController() {
    String autoStaticImportMessage =
      JavaBundle.message("auto.static.import.comment");
    String excludeStaticImportMessage =
      JavaBundle.message("exclude.from.imports.no.exclusions");
    return OptionContainer.super.getOptionController()
      .withRootPane(() -> OptPane.pane(
        OptPane.stringList("includedAutoStaticNames", autoStaticImportMessage),
        OptPane.stringList("excludedNames", excludeStaticImportMessage))
      );
  }

  /**
   * Provides bindId = "JavaProjectCodeInsightSettings.excludedNames" and
   * "JavaProjectCodeInsightSettings.includedAutoStaticNames" lists to control auto-imports
   */
  public static final class Provider implements OptionControllerProvider {
    @Override
    public @NotNull OptionController forContext(@NotNull PsiElement context) {
      return getSettings(context.getProject()).getOptionController();
    }

    @Override
    public @NotNull String name() {
      return "JavaProjectCodeInsightSettings";
    }
  }
}
