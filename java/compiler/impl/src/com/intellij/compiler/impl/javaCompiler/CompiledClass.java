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
package com.intellij.compiler.impl.javaCompiler;

/**
* @author cdr
*/
final class CompiledClass {
  public final int qName;
  public final String relativePathToSource;
  public final String pathToClass;

  CompiledClass(final int qName, final String relativePathToSource, final String pathToClass) {
    this.qName = qName;
    this.relativePathToSource = relativePathToSource;
    this.pathToClass = pathToClass;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final CompiledClass that = (CompiledClass)o;

    if (qName != that.qName) return false;
    if (!pathToClass.equals(that.pathToClass)) return false;
    if (!relativePathToSource.equals(that.relativePathToSource)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = qName;
    result = 31 * result + relativePathToSource.hashCode();
    result = 31 * result + pathToClass.hashCode();
    return result;
  }

  public String toString() {
    return "[" + pathToClass + ";" + relativePathToSource + "]";
  }
}
