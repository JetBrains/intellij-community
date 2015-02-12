/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.requests;

import com.intellij.diff.contents.DiffContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimpleDiffRequest extends ContentDiffRequest {
  @Nullable private final String myTitle;
  @NotNull private final DiffContent[] myContents;
  @NotNull private final String[] myContentTitles;

  public SimpleDiffRequest(@Nullable String title,
                           @NotNull DiffContent content1,
                           @NotNull DiffContent content2,
                           @NotNull String title1,
                           @NotNull String title2) {
    this(title, new DiffContent[]{content1, content2}, new String[]{title1, title2});
  }

  public SimpleDiffRequest(@Nullable String title,
                           @NotNull DiffContent[] contents,
                           @NotNull String[] titles) {
    assert contents.length == titles.length;

    myTitle = title;
    myContents = contents;
    myContentTitles = titles;
  }

  @NotNull
  @Override
  public DiffContent[] getContents() {
    return myContents;
  }

  @NotNull
  @Override
  public String[] getContentTitles() {
    return myContentTitles;
  }

  @Nullable
  @Override
  public String getTitle() {
    return myTitle;
  }

  @Override
  public void onAssigned(boolean isAssigned) {
    for (DiffContent content : myContents) {
      content.onAssigned(isAssigned);
    }
  }
}
