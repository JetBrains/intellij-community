// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a table of lines, each line is a separate OptStringList control but the elements 
 * in the corresponding lists are synchronized.
 * 
 * @param label label for the whole table
 * @param children list of columns 
 */
public record OptTable(@NotNull LocMessage label, @NotNull List<@NotNull OptTableColumn> children,
                       @Nullable HtmlChunk description) implements OptRegularComponent,
                                                                   OptDescribedComponent {
  @Override
  public @NotNull OptRegularComponent prefix(@NotNull String bindPrefix) {
    return new OptTable(label, ContainerUtil.map(children, child -> child.prefix(bindPrefix)), description);
  }

  /**
   * @param description textual description
   * @return an equivalent table but with a description
   * @throws IllegalStateException if description was already set
   */
  @Override
  public OptTable description(@NotNull @NlsContexts.Tooltip String description) {
    return description(HtmlChunk.text(description));
  }

  /**
   * @param description HTML description
   * @return an equivalent table but with a description
   * @throws IllegalStateException if description was already set
   */
  @Override
  public OptTable description(@NotNull HtmlChunk description) {
    if (this.description != null) {
      throw new IllegalStateException("Description is already set");
    }
    return new OptTable(label, children, description);
  }
}
