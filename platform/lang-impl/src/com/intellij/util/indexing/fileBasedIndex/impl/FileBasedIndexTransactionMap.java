/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.indexing.fileBasedIndex.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

public class FileBasedIndexTransactionMap implements ApplicationComponent {
  private final Map<Document, PsiFile> myTransactionMap = new THashMap<Document, PsiFile>();

  public void put(Document doc, PsiFile file) {
    myTransactionMap.put(doc, file);
  }

  public void remove(Document doc) {
    myTransactionMap.remove(doc);
  }

  public boolean isEmpty() {
    return myTransactionMap.isEmpty();
  }

  public Collection<? extends Document> keySet() {
    return myTransactionMap.keySet();
  }

  public PsiFile get(Document document) {
    return myTransactionMap.get(document);
  }


  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "FileBasedIndexTransactionMap";
  }
}
