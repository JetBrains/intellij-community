// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.console;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@State(name = "ConsoleFoldingSettings", storages = @Storage("consoleFolding.xml"), category = SettingsCategory.CODE)
public final class ConsoleFoldingSettings implements PersistentStateComponent<ConsoleFoldingSettings.MyBean> {
  private final List<String> myPositivePatterns = new ArrayList<>();
  private final List<String> myNegativePatterns = new ArrayList<>();

  public ConsoleFoldingSettings() {
    for (CustomizableConsoleFoldingBean regexp : CustomizableConsoleFoldingBean.EP_NAME.getExtensions()) {
      patternList(regexp.negate).add(regexp.substring);
    }
    CustomizableConsoleFoldingBean.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull CustomizableConsoleFoldingBean extension, @NotNull PluginDescriptor pluginDescriptor) {
        patternList(extension.negate).add(extension.substring);
      }

      @Override
      public void extensionRemoved(@NotNull CustomizableConsoleFoldingBean extension, @NotNull PluginDescriptor pluginDescriptor) {
        patternList(extension.negate).remove(extension.substring);
      }
    }, null);
  }

  public static ConsoleFoldingSettings getSettings() {
    return ApplicationManager.getApplication().getService(ConsoleFoldingSettings.class);
  }

  public boolean shouldFoldLine(String line) {
    return containsAny(line, myPositivePatterns) && !containsAny(line, myNegativePatterns);
  }

  private static boolean containsAny(String line, List<String> patterns) {
    Set<String> lines = null;
    for (ConsoleLineModifier modifier : ConsoleLineModifier.EP_NAME.getExtensionList()) {
      String modifiedLine = modifier.modify(line);
      if (modifiedLine != null) {
        if (lines == null) {
          lines = new HashSet<>();
          lines.add(line);
        }
        lines.add(modifiedLine);
      }
    }

    Condition<String> containsPredicate = l -> {
      for (String pattern : patterns) {
        if (l.contains(pattern)) {
          return true;
        }
      }
      return false;
    };

    if (lines == null) {
      return containsPredicate.value(line);
    }

    return ContainerUtil.exists(lines, containsPredicate);
  }

  public List<String> getPositivePatterns() {
    return myPositivePatterns;
  }

  public List<String> getNegativePatterns() {
    return myNegativePatterns;
  }

  @Override
  public MyBean getState() {
    MyBean result = new MyBean();
    writeDiff(result.addedPositive, result.removedPositive, false);
    writeDiff(result.addedNegative, result.removedNegative, true);
    return result;
  }

  private void writeDiff(List<? super String> added, List<? super String> removed, boolean negated) {
    Set<String> baseline = new HashSet<>();
    for (CustomizableConsoleFoldingBean regexp : CustomizableConsoleFoldingBean.EP_NAME.getExtensionList()) {
      if (regexp.negate == negated) {
        baseline.add(regexp.substring);
      }
    }

    List<String> current = patternList(negated);
    added.addAll(current);
    added.removeAll(baseline);

    baseline.removeAll(current);
    removed.addAll(baseline);
  }

  private List<String> patternList(boolean negated) {
    return negated ? myNegativePatterns : myPositivePatterns;
  }

  private static Collection<String> filterEmptyStringsFromCollection(Collection<String> collection) {
    return ContainerUtil.filter(collection, input -> !StringUtil.isEmpty(input));
  }

  @Override
  public void loadState(@NotNull MyBean state) {
    myPositivePatterns.clear();
    myNegativePatterns.clear();

    Set<String> removedPositive = new HashSet<>(state.removedPositive);
    Set<String> removedNegative = new HashSet<>(state.removedNegative);

    for (CustomizableConsoleFoldingBean regexp : CustomizableConsoleFoldingBean.EP_NAME.getExtensions()) {
      if (!(regexp.negate ? removedNegative : removedPositive).contains(regexp.substring)) {
        patternList(regexp.negate).add(regexp.substring);
      }
    }

    myPositivePatterns.addAll(filterEmptyStringsFromCollection(state.addedPositive));
    myNegativePatterns.addAll(filterEmptyStringsFromCollection(state.addedNegative));

  }

  public static class MyBean {
    public List<String> addedPositive = new ArrayList<>();
    public List<String> addedNegative = new ArrayList<>();
    public List<String> removedPositive = new ArrayList<>();
    public List<String> removedNegative = new ArrayList<>();
  }

}
