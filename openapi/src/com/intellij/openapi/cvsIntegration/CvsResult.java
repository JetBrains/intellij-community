/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.cvsIntegration;

import com.intellij.openapi.vcs.VcsException;

import java.util.Collection;
import java.util.List;

public interface CvsResult {
  void setIsCanceled();

  List<VcsException> getErrors();

  List<VcsException> getWarnings();

  boolean isCanceled();

  boolean isLoggedIn();

  void addAllErrors(Collection<VcsException> errors);

  void addAllWarnings(Collection<VcsException> warnings);

  void setIsLoggedIn();

  boolean hasNoErrors();

  VcsException composeError();

  void addError(VcsException error);

  List<VcsException> getErrorsAndWarnings();
}