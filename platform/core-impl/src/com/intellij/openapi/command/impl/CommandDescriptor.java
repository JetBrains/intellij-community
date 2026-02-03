// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.CommandToken;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.Command;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;


final class CommandDescriptor implements CommandToken {
  private final @NotNull CommandIdentity identity;
  private final @NotNull Runnable command;
  private final @Nullable Project project;
  private final @Nullable @Command String name;
  private final @Nullable Object groupId;
  private final @Nullable Document document;
  private final @NotNull UndoConfirmationPolicy undoConfirmationPolicy;
  private final boolean shouldRecordActionForActiveDocument;

  CommandDescriptor(
    @NotNull Runnable command,
    @Nullable Project project,
    @Nullable @Command String name,
    @Nullable Object groupId,
    @NotNull UndoConfirmationPolicy undoConfirmationPolicy,
    boolean shouldRecordActionForActiveDocument,
    @Nullable Document document
  ) {
    this(
      new CommandIdentity(),
      command,
      project,
      name,
      groupId,
      undoConfirmationPolicy,
      shouldRecordActionForActiveDocument,
      document
    );
  }

  private CommandDescriptor(
    @NotNull CommandIdentity identity,
    @NotNull Runnable command,
    @Nullable Project project,
    @Nullable @Command String name,
    @Nullable Object groupId,
    @NotNull UndoConfirmationPolicy undoConfirmationPolicy,
    boolean shouldRecordActionForActiveDocument,
    @Nullable Document document
  ) {
    this.identity = identity;
    this.command = command;
    this.project = project;
    this.name = name;
    this.groupId = groupId;
    this.undoConfirmationPolicy = undoConfirmationPolicy;
    this.shouldRecordActionForActiveDocument = shouldRecordActionForActiveDocument;
    this.document = document;
  }

  @NotNull CommandEvent toCommandEvent(@NotNull CommandProcessor processor) {
    return new CommandEvent(
      processor,
      command,
      name,
      groupId,
      project,
      undoConfirmationPolicy,
      shouldRecordActionForActiveDocument,
      document
    );
  }

  @NotNull CommandDescriptor withName(@Nullable @Command String name) {
    return new CommandDescriptor(
      identity,
      command,
      project,
      name,
      groupId,
      undoConfirmationPolicy,
      shouldRecordActionForActiveDocument,
      document
    );
  }

  @NotNull CommandDescriptor withGroupId(@Nullable Object groupId) {
    return new CommandDescriptor(
      identity,
      command,
      project,
      name,
      groupId,
      undoConfirmationPolicy,
      shouldRecordActionForActiveDocument,
      document
    );
  }

  @Override
  public @Nullable Project getProject() {
    return project;
  }

  @NotNull Runnable getCommand() {
    return command;
  }

  @Nullable @Command String getName() {
    return name;
  }

  @Nullable Object getGroupId() {
    return groupId;
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof CommandDescriptor)) return false;
    CommandDescriptor that = (CommandDescriptor)object;
    return Objects.equals(identity, that.identity);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(identity);
  }

  @Override
  public String toString() {
    return "'" + name + "', group: '" + groupId + "'";
  }
}
