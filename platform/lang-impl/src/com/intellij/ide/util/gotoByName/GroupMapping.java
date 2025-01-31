// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.UpdateSession;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GroupMapping implements Comparable<GroupMapping> {
  private static final Pattern INNER_GROUP_WITH_IDS = Pattern.compile("(.*) \\(\\d+\\)");

  private final boolean myShowNonPopupGroups;
  private final List<List<ActionGroup>> myPaths = new ArrayList<>();

  private @Nullable @NlsActions.ActionText String myBestGroupName;
  private boolean myBestNameComputed;

  public GroupMapping() {
    this(false);
  }

  public GroupMapping(boolean showNonPopupGroups) {
    myShowNonPopupGroups = showNonPopupGroups;
  }

  public static @NotNull GroupMapping createFromText(@NlsActions.ActionText String text, boolean showGroupText) {
    GroupMapping mapping = new GroupMapping(showGroupText);
    mapping.addPath(Collections.singletonList(new DefaultActionGroup(text, false)));
    return mapping;
  }

  void addPath(@NotNull List<ActionGroup> path) {
    myPaths.add(path);
  }


  @Override
  public int compareTo(@NotNull GroupMapping o) {
    return Comparing.compare(getFirstGroupName(), o.getFirstGroupName());
  }

  public @NlsActions.ActionText @Nullable String getBestGroupName() {
    if (myBestNameComputed) return myBestGroupName;
    return getFirstGroupName();
  }

  public @Nullable List<ActionGroup> getFirstGroup() {
    return ContainerUtil.getFirstItem(myPaths);
  }

  private @Nls @Nullable String getFirstGroupName() {
    List<ActionGroup> path = getFirstGroup();
    return path != null ? getPathName(path) : null;
  }

  public void updateBeforeShow(@NotNull UpdateSession session) {
    if (myBestNameComputed) return;
    myBestNameComputed = true;

    for (List<ActionGroup> path : myPaths) {
      String name = getActualPathName(path, session);
      if (name != null) {
        myBestGroupName = name;
        return;
      }
    }
  }

  public @NotNull List<String> getAllGroupNames() {
    return ContainerUtil.map(myPaths, path -> getPathName(path));
  }

  private @Nls @Nullable String getPathName(@NotNull List<? extends ActionGroup> path) {
    String name = "";
    for (ActionGroup group : path) {
      name = appendGroupName(name, group, group.getTemplatePresentation());
    }
    return StringUtil.nullize(name);
  }

  private @Nls @Nullable String getActualPathName(@NotNull List<? extends ActionGroup> path, @NotNull UpdateSession session) {
    String name = "";
    for (ActionGroup group : path) {
      Presentation presentation = session.presentation(group);
      if (!presentation.isVisible()) return null;
      name = appendGroupName(name, group, presentation);
    }
    return StringUtil.nullize(name);
  }

  private @Nls @NotNull String appendGroupName(@NotNull @Nls String prefix, @NotNull ActionGroup group, @NotNull Presentation presentation) {
    if (group.isPopup() || myShowNonPopupGroups) {
      String groupName = getActionGroupName(presentation);
      if (!StringUtil.isEmptyOrSpaces(groupName)) {
        return prefix.isEmpty()
               ? groupName
               : prefix + " | " + groupName;
      }
    }
    return prefix;
  }

  private static @NlsActions.ActionText @Nullable String getActionGroupName(@NotNull Presentation presentation) {
    String text = presentation.getText();
    if (text == null) return null;

    Matcher matcher = INNER_GROUP_WITH_IDS.matcher(text);
    if (matcher.matches()) return matcher.group(1);

    return text;
  }
}
