// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.search;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class OptionDescription implements Comparable<OptionDescription> {
  private final @Nls String myOption;
  private final @NlsSafe String myHit;
  private final @NlsSafe String myPath;
  private final @NonNls String myConfigurableId;
  private final @Nls String myGroupName;

  public OptionDescription(@NlsSafe String hit) {
    this(null, hit, null);
  }

  public OptionDescription(@Nls String option, @NlsSafe String hit, @NlsSafe String path) {
    this(option, null, hit, path);
  }

  public OptionDescription(@Nls String option, @NonNls String configurableId, @NlsSafe String hit, @NlsSafe String path) {
    this(option, configurableId, hit, path, null);
  }

  public OptionDescription(@Nls String option,
                           @NonNls String configurableId,
                           @NlsSafe String hit,
                           @NlsSafe String path,
                           @Nls String groupName) {
    myOption = option;
    myHit = hit;
    myPath = path;
    myConfigurableId = configurableId;
    myGroupName = groupName;
  }

  public @Nls String getOption() {
    return myOption;
  }

  public final @NlsSafe @Nullable String getHit() {
    return myHit;
  }

  public final @Nullable String getPath() {
    return myPath;
  }

  public final @NonNls String getConfigurableId() {
    return myConfigurableId;
  }

  public final @Nls String getGroupName() {
    return myGroupName;
  }

  public @NlsSafe String getValue() {
    return null;
  }

  public boolean hasExternalEditor() {
    return false;
  }

  public void invokeInternalEditor() {
  }

  public final String toString() {
    return myHit;
  }

  public final boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final OptionDescription that = (OptionDescription)o;

    if (!Objects.equals(myConfigurableId, that.myConfigurableId)) return false;
    if (!Objects.equals(myHit, that.myHit)) return false;
    if (!Objects.equals(myOption, that.myOption)) return false;
    if (!Objects.equals(myPath, that.myPath)) return false;

    return true;
  }

  public final int hashCode() {
    int result;
    result = myOption != null ? myOption.hashCode() : 0;
    result = 31 * result + (myHit != null ? myHit.hashCode() : 0);
    result = 31 * result + (myPath != null ? myPath.hashCode() : 0);
    result = 31 * result + (myConfigurableId != null ? myConfigurableId.hashCode() : 0);
    return result;
  }

  @Override
  public final int compareTo(final OptionDescription o) {
    String hit1 = StringUtil.notNullize(myHit);
    String hit2 = StringUtil.notNullize(o.getHit());
    int diff = hit1.compareTo(hit2);
    if (diff != 0) return diff;

    String option1 = myOption;
    String option2 = o.getOption();
    if (option1 != null && option2 != null) {
      return option1.compareTo(option2);
    }
    else if (option1 != null || option2 != null) {
      return option1 == null ? 1 : -1; // nulls go last
    }
    else {
      return 0;
    }
  }
}
