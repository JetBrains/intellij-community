// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.editor;

import com.intellij.openapi.editor.Document;

import java.util.ArrayList;

public class DocumentOfPagesModel {

  private final Document myDocument;
  private final ArrayList<Page> pagesInDocument = new ArrayList<>();

  DocumentOfPagesModel(Document document) {
    myDocument = document;
  }

  public Document getDocument() {
    return myDocument;
  }

  public int getPagesAmount() {
    return pagesInDocument.size();
  }

  public ArrayList<Page> getPagesList() {
    return pagesInDocument;
  }

  public Page getPageByIndex(int index) {
    return pagesInDocument.get(index);
  }
}
