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
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: 11/18/12
 */
public class CompiledClass extends UserDataHolderBase{
  @NotNull
  private final File myOutputFile;
  @NotNull
  private final File mySourceFile;
  @Nullable
  private final String myClassName;
  @NotNull
  private BinaryContent myContent;

  private boolean myIsDirty = false;

  public CompiledClass(@NotNull File outputFile, @NotNull File sourceFile, @Nullable String className, @NotNull BinaryContent content) {
    myOutputFile = outputFile;
    mySourceFile = sourceFile;
    myClassName = className;
    myContent = content;
  }

  public  void save() throws IOException {
    myContent.saveToFile(myOutputFile);
    myIsDirty = false;
  }

  @NotNull
  public File getOutputFile() {
    return myOutputFile;
  }

  @NotNull
  public File getSourceFile() {
    return mySourceFile;
  }

  @Nullable
  public String getClassName() {
    return myClassName;
  }

  @NotNull
  public BinaryContent getContent() {
    return myContent;
  }

  public void setContent(@NotNull BinaryContent content) {
    myContent = content;
    myIsDirty = true;
  }

  public boolean isDirty() {
    return myIsDirty;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CompiledClass aClass = (CompiledClass)o;

    if (!FileUtil.filesEqual(myOutputFile, aClass.myOutputFile)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return FileUtil.fileHashCode(myOutputFile);
  }

  @Override
  public String toString() {
    return "CompiledClass{" +
           "myOutputFile=" + myOutputFile +
           ", mySourceFile=" + mySourceFile +
           ", myIsDirty=" + myIsDirty +
           '}';
  }
}
