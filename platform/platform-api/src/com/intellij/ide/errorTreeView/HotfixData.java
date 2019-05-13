/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.errorTreeView;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

public class HotfixData {
  private final String myId;
  private final String myErrorText;
  private final String myFixComment;
  private final Consumer<HotfixGate> myFix;

  public HotfixData(@NotNull final String id, @NotNull final String errorText, @NotNull String fixComment, final Consumer<HotfixGate> fix) {
    myErrorText = errorText;
    myFixComment = fixComment;
    myFix = fix;
    myId = id;
  }

  public String getId() {
    return myId;
  }

  public String getErrorText() {
    return myErrorText;
  }

  public Consumer<HotfixGate> getFix() {
    return myFix;
  }

  public String getFixComment() {
    return myFixComment;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HotfixData that = (HotfixData)o;

    if (!myId.equals(that.myId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }
}
