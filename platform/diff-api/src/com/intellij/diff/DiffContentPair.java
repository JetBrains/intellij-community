/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.diff;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import org.jetbrains.annotations.Nullable;

/**
 * Created by sashakir on 09/06/16.
 */
public class DiffContentPair {
  private DocumentContent leftContent;
  private DocumentContent rightContent;
  private String myFilePath;

  public DiffContentPair(DocumentContent leftContent, DocumentContent rightContent, String filePath) {
    this.leftContent = leftContent;
    this.rightContent = rightContent;
    this.myFilePath = filePath;
  }

  @Nullable
  public DocumentContent getLeftContent() {
    return leftContent;
  }

  @Nullable
  public DocumentContent getRightContent() {
    return rightContent;
  }

  public String getFilePath() {
    return myFilePath;
  }
}
