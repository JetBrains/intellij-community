/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.autodetect;

public class IndentUsageInfo {
  private final int indentSize;
  private final int timesUsed;

  public IndentUsageInfo(int indentSize, int timesUsed) {
    this.indentSize = indentSize;
    this.timesUsed = timesUsed;
  }

  public int getIndentSize() {
    return indentSize;
  }

  public int getTimesUsed() {
    return timesUsed;
  }

  @Override
  public String toString() {
    return "indent: " + indentSize + ", used " + timesUsed;
  }
}
