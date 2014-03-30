/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.editor;

public class IndentGuideDescriptor {
  public final int indentLevel;
  public final int startLine;
  public final int endLine;

  public IndentGuideDescriptor(int indentLevel, int startLine, int endLine) {
    this.indentLevel = indentLevel;
    this.startLine = startLine;
    this.endLine = endLine;
  }

  @Override
  public int hashCode() {
    int result = indentLevel;
    result = 31 * result + startLine;
    result = 31 * result + endLine;
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IndentGuideDescriptor that = (IndentGuideDescriptor)o;

    if (endLine != that.endLine) return false;
    if (indentLevel != that.indentLevel) return false;
    if (startLine != that.startLine) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("%d (%d-%d)", indentLevel, startLine, endLine);
  }
}
