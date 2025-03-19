// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import org.jetbrains.annotations.ApiStatus;

/**
 * @author Roman.Chernyatchik
 */
@ApiStatus.OverrideOnly
public interface InspectionToolCmdlineOptions extends InspectionToolCmdlineOptionHelpProvider {
  /**
   * @param app Inspection Application
   */
  void initApplication(InspectionApplicationBase app);

  /**
   * @return 0 if turned off
   */
  int getVerboseLevelProperty();

  /**
   * @return If true help message wont be outputted
   */
  boolean suppressHelp();

  void validate() throws CmdlineArgsValidationException;

  /**
   * Application components have been already initialized at this moment.
   * E.g. you can save smth in application component or service
   */
  void beforeStartup();

  final class CmdlineArgsValidationException extends Exception {
    public CmdlineArgsValidationException(String message) {
      super(message);
    }
  }
}
