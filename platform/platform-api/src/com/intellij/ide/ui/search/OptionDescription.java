// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.ui.search;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class OptionDescription implements Comparable {
  private final String myOption;
  private final String myHit;
  private final String myPath;
  private final String myConfigurableId;
  private final String myGroupName;

  public OptionDescription(String hit) {
    this(null, hit, null);
  }

  public OptionDescription(String option, String hit, String path) {
    this(option, null, hit, path);
  }

  public OptionDescription(String option, String configurableId, String hit, String path) {
    this(option, configurableId, hit, path, null);
  }

  public OptionDescription(String option, String configurableId, String hit, String path, String groupName) {
    myOption = option;
    myHit = hit;
    myPath = path;
    myConfigurableId = configurableId;
    myGroupName = groupName;
  }

  public String getOption() {
    return myOption;
  }

  @Nullable
  public String getHit() {
    return myHit;
  }

  @Nullable
  public String getPath() {
    return myPath;
  }

  public String getConfigurableId() {
    return myConfigurableId;
  }

  public String getGroupName() {
    return myGroupName;
  }

  public String getValue() {
    return null;
  }

  public boolean hasExternalEditor() {
    return false;
  }

  public void invokeInternalEditor() {
  }

  public String toString() {
    return myHit;
  }


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final OptionDescription that = (OptionDescription)o;

    if (!Objects.equals(myConfigurableId, that.myConfigurableId)) return false;
    if (!Objects.equals(myHit, that.myHit)) return false;
    if (!Objects.equals(myOption, that.myOption)) return false;
    if (!Objects.equals(myPath, that.myPath)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myOption != null ? myOption.hashCode() : 0);
    result = 31 * result + (myHit != null ? myHit.hashCode() : 0);
    result = 31 * result + (myPath != null ? myPath.hashCode() : 0);
    result = 31 * result + (myConfigurableId != null ? myConfigurableId.hashCode() : 0);
    return result;
  }

  @Override
  public int compareTo(final Object o) {
    OptionDescription description = ((OptionDescription)o);
    if (Comparing.strEqual(myHit, description.getHit())) {
      return myOption != null ? myOption.compareTo(description.getOption()) : 0;
    }
    if (myHit != null && description.getHit() != null) {
      return myHit.compareTo(description.getHit());
    }
    return 0;
  }
}
