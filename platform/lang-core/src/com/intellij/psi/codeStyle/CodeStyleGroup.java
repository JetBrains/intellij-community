// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a group of code style configurables sharing the same {@code CodeStyleGroup} instance. The group is shown as a separate node
 * in settings UI with child configurables under it.
 */
public class CodeStyleGroup {
  private final @NlsContexts.ConfigurableName String myDisplayName;
  private final @NlsContexts.DetailedDescription String myDescription;
  private final @NonNls String myId;
  private final @NonNls String myHelpTopic;

  /**
   * Constructor.
   *
   * @param groupId     The unique group ID.
   * @param displayName The display name as shown in settings UI.
   */
  public CodeStyleGroup(@NonNls @NotNull String groupId,
                        @NlsContexts.ConfigurableName @NotNull String displayName,
                        @NlsContexts.DetailedDescription @NotNull String description) {
    this(groupId, displayName, description, null);
  }

  /**
   * Constructor.
   *
   * @param groupId     The unique group ID.
   * @param displayName The display name as shown in settings UI.
   * @param helpTopic   The help topic associated with the group.
   */
  public CodeStyleGroup(@NonNls @NotNull String groupId,
                        @NlsContexts.ConfigurableName @NotNull String displayName,
                        @NlsContexts.DetailedDescription @NotNull String description,
                        @NonNls @Nullable String helpTopic) {
    myId = groupId;
    myDisplayName = displayName;
    myDescription = description;
    myHelpTopic = helpTopic;
  }

  /**
   * @return The group display name.
   */
  public @NlsContexts.ConfigurableName @NotNull String getDisplayName() {
    return myDisplayName;
  }

  public @NlsContexts.DetailedDescription @NotNull String getDescription() {
    return myDescription;
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
