// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;


import org.jetbrains.annotations.NotNull;

/**
 * Represents a group of code style configurables sharing the same {@code CodeStyleGroup} instance. The group is shown as a separate node
 * in settings UI with child configurables under it.
 */
public class CodeStyleGroup {
  private final String myDisplayName;
  private final String myId;

  /**
   * Constructor.
   *
   * @param groupId     The unique group ID.
   * @param displayName The display name as shown in settings UI.
   */
  public CodeStyleGroup(@NotNull String groupId, @NotNull String displayName) {
    myId = groupId;
    myDisplayName = displayName;
  }

  /**
   * @return The group display name.
   */
  @NotNull
  public String getDisplayName() {
    return myDisplayName;
  }

  /**
   * @return The group ID.
   */
  public String getId() {
    return myId;
  }
}
