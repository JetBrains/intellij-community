// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.ide.CliResult;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.ArrayUtilRt;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Future;

/**
 * <p>This extension point allows running a custom [command-line] application based on the IntelliJ platform.</p>
 *
 * <p>A command may come directly (there were no other instances of the application running, so the command starts a new one),
 * or externally (the application has detected a running instance and passed a command to it). In the former case, the platform
 * invokes {@link #premain} and {@link #main} methods, in the latter - {@link #processExternalCommandLineAsync}.</p>
 */
public interface ApplicationStarter {
  ExtensionPointName<ApplicationStarter> EP_NAME = new ExtensionPointName<>("com.intellij.appStarter");

  int NON_MODAL = 1;
  int ANY_MODALITY = 2;
  int NOT_IN_EDT = 3;

  /**
   * Command-line switch to start with this runner.
   * For example, return {@code "inspect"} if you'd like to start an app with {@code "idea.exe inspect ..."} command.
   *
   * @return command-line selector.
   */
  String getCommandName();

  /**
   * Called before application initialization.
   *
   * @param args program arguments (including the command)
   */
  default void premain(@NotNull List<String> args) {
    premain(ArrayUtilRt.toStringArray(args));
  }

  /**
   * <p>Called when application has been initialized. Invoked in event dispatch thread.</p>
   * <p>An application starter should take care of terminating JVM when appropriate by calling {@link System#exit}.</p>
   *
   * @param args program arguments (including the selector)
   */
  default void main(@NotNull List <String> args) {
    main(ArrayUtilRt.toStringArray(args));
  }

  /**
   * Applications that are incapable of working in a headless mode should override the method and return {@code false}.
   */
  default boolean isHeadless() {
    return true;
  }

  /**
   * Applications that are capable of processing command-line arguments within a running IDE instance
   * should return {@code true} from this method and implement {@link #processExternalCommandLineAsync}.
   *
   * @see #processExternalCommandLineAsync
   */
  default boolean canProcessExternalCommandLine() {
    return false;
  }

  /**
   * Return {@link #ANY_MODALITY} if handling the command requires EDT and can be executed even when there is a modal dialog open.
   * Such a starter may not directly change the PSI/VFS/project model of the opened projects, open new projects,
   * or perform activities that mandate write-safe contexts (see {@link TransactionGuard}).
   * <p>
   * Return {@link #NOT_IN_EDT} if handling the command can be performed on a background thread (please note that the platform
   * may ignore the flag and process a command as {@link #NON_MODAL}).
   */
  @MagicConstant(intValues = {NON_MODAL, ANY_MODALITY, NOT_IN_EDT})
  default int getRequiredModality() {
    return NON_MODAL;
  }

  /** @see #canProcessExternalCommandLine */
  default @NotNull Future<CliResult> processExternalCommandLineAsync(@NotNull List<String> args, @Nullable String currentDirectory) {
    throw new UnsupportedOperationException("Class " + getClass().getName() + " must implement `processExternalCommandLineAsync()`");
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated Use {@link #premain(List)} */
  @Deprecated(forRemoval = true)
  default void premain(@SuppressWarnings("unused") String @NotNull [] args) { }

  /** @deprecated Use {@link #main(List)} */
  @Deprecated(forRemoval = true)
  default void main(String @NotNull [] args) { }
  //</editor-fold>
}
