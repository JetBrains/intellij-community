// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.editor;

class AbsoluteCaretPostioin {

  long pageNumber;
  int symbolOffsetInPage;

  public AbsoluteCaretPostioin(long pageNumber, int symbolOffsetInPage) {
    this.pageNumber = pageNumber;
    this.symbolOffsetInPage = symbolOffsetInPage;
  }

  void set(long pageNumber, int symbolOffsetInPage) {
    this.pageNumber = pageNumber;
    this.symbolOffsetInPage = symbolOffsetInPage;
  }
}
