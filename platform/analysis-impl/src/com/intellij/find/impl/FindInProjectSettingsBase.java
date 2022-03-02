// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class FindInProjectSettingsBase implements PersistentStateComponent<FindInProjectSettingsBase> {
  private static final int MAX_RECENT_SIZE = 30;

  @XCollection(style = XCollection.Style.v2, elementName = "find", valueAttributeName = "")
  private final List<String> findStrings = new ArrayList<>();

  @XCollection(style = XCollection.Style.v2, elementName = "replace", valueAttributeName = "")
  private final List<String> replaceStrings = new ArrayList<>();

  @XCollection(style = XCollection.Style.v2, elementName = "dir", valueAttributeName = "")
  private final List<String> dirStrings = new ArrayList<>();

  @Override
  public final void loadState(@NotNull FindInProjectSettingsBase state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public final void initializeComponent() {
    //Avoid duplicates
    LinkedHashSet<String> tmp = new LinkedHashSet<>(findStrings);
    findStrings.clear();
    findStrings.addAll(tmp);

    tmp.clear();
    tmp.addAll(replaceStrings);
    replaceStrings.clear();
    replaceStrings.addAll(tmp);

    tmp.clear();
    tmp.addAll(dirStrings);
    dirStrings.clear();
    dirStrings.addAll(tmp);
  }

  @Override
  public FindInProjectSettingsBase getState() {
    return this;
  }

  public void addDirectory(@NotNull @NlsSafe String s) {
    addRecentStringToList(s, dirStrings);
  }

  public @NotNull List<String> getRecentDirectories() {
    return new ArrayList<>(dirStrings);
  }

  public void addStringToFind(@NotNull @NlsSafe String s) {
    if (s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0) {
      return;
    }
    addRecentStringToList(s, findStrings);
  }

  public void addStringToReplace(@NotNull @NlsSafe String s) {
    if (s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0) {
      return;
    }
    addRecentStringToList(s, replaceStrings);
  }

  public @NlsSafe String @NotNull [] getRecentFindStrings() {
    return ArrayUtilRt.toStringArray(findStrings);
  }

  public @NlsSafe String @NotNull [] getRecentReplaceStrings() {
    return ArrayUtilRt.toStringArray(replaceStrings);
  }

  static void addRecentStringToList(@Nullable @NlsSafe String str,
                                    @NotNull List<? super String> list) {
    if (StringUtil.isEmptyOrSpaces(str)) {
      return;
    }

    list.remove(str);
    list.add(str);
    while (list.size() > MAX_RECENT_SIZE) {
      list.remove(0);
    }
  }

  static final class FindInProjectPathMacroFilter extends PathMacroFilter {
    @Override
    public boolean skipPathMacros(@NotNull Element element) {
      String tag = element.getName();
      // dirStrings must be replaced, so, we must not skip it
      if (tag.equals("findStrings") || tag.equals("replaceStrings")) {
        String component = FileStorageCoreUtil.findComponentName(element);
        return component != null && (component.equals("FindSettings") || component.equals("FindInProjectRecents"));
      }
      return false;
    }
  }
}
