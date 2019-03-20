// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.accessGettingPageTokens;

import com.intellij.openapi.util.UserDataHolderBase;

public class AccessGettingPageToken extends UserDataHolderBase {
  private Reason reason;
  private long pageNumber;

  public AccessGettingPageToken(Reason reason, long pageNumber) {
    this.reason = reason;
    this.pageNumber = pageNumber;
  }

  public Reason getReason() {
    return reason;
  }

  public long getPageNumber() {
    return pageNumber;
  }

  @Override
  public String toString() {
    return "reason=" + reason.toString() + " pageNumber=" + pageNumber + "";
  }
}
