// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

/** Terminates the process with the given message, without a stack trace. */
public class InspectionApplicationException extends Exception {
  public InspectionApplicationException(String message) {
    super(message);
  }
}
