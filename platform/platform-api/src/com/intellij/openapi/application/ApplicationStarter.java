// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.ide.CliResult;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.ArrayUtilRt;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Future;

/**
 * This extension point allows running custom [command-line] application based on IntelliJ platform.
 */
public interface ApplicationStarter {
  ExtensionPointName<ApplicationStarter> EP_NAME = new ExtensionPointName<>("com.intellij.appStarter");

  int NON_MODAL = 1;
  int ANY_MODALITY = 2;
  int NOT_IN_EDT = 3;

  /**
   * Command-line switch to start with this runner.
   * For example return {@code "inspect"} if you'd like to start an app with {@code "idea.exe inspect ..."} command).
   *
   * @return command-line selector.
   */
  @NonNls String getCommandName();

  /**
   * Called before application initialization.
   *
   * @param args program arguments (including the selector)
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
   * Return {@link #ANY_MODALITY} if the command of this launcher can be processed when there is a modal dialog open.
   * Such a starter may not directly change the PSI/VFS/project model of the opened projects or open new projects.
   * Such a starter may not perform activities that should be performed inside write-safe contexts (see {@link TransactionGuard}).
   * <p>
   * Return {@link #NOT_IN_EDT} if the command of this launcher can be processed on pooled thread.
   * <p>
   * Note, that platform may ignore this flag and process command as {@link #NON_MODAL}.
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
  @Deprecated
  default void premain(@SuppressWarnings("unused") String @NotNull [] args) { }

  /** @deprecated Use {@link #main(List)} */
  @Deprecated
  default void main(String @NotNull [] args) { }

  //</editor-fold>
}
