// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

/**
 * Represents a group of code style configurables sharing the same {@code CodeStyleGroup} instance. The group is shown as a separate node
 * in settings UI with child configurables under it.
 */
public class CodeStyleGroup {
  private final @Nls(capitalization = Title) String myDisplayName;
  private final @NonNls String myId;
  private final @NonNls String myHelpTopic;

  /**
   * Constructor.
   *
   * @param groupId     The unique group ID.
   * @param displayName The display name as shown in settings UI.
   */
  public CodeStyleGroup(@NonNls @NotNull String groupId, @Nls(capitalization = Title) @NotNull String displayName) {
    this(groupId, displayName, null);
  }

  /**
   * Constructor.
   *
   * @param groupId     The unique group ID.
   * @param displayName The display name as shown in settings UI.
   * @param helpTopic   The help topic associated with the group.
   */
  public CodeStyleGroup(@NonNls @NotNull String groupId, @Nls(capitalization = Title) @NotNull String displayName, @NonNls @Nullable String helpTopic) {
    myId = groupId;
    myDisplayName = displayName;
    myHelpTopic = helpTopic;
  }

  /**
   * @return The group display name.
   */
  @NotNull
  public @Nls(capitalization = Title) String getDisplayName() {
    return myDisplayName;
  }

  /**
   * @return The group ID.
   */
  public @NonNls String getId() {
    return myId;
  }

  @Nullable
  public @NonNls String getHelpTopic() {
    return myHelpTopic;
  }
}
