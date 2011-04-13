/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.diff;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * @author Konstantin Bulenkov
 */
public abstract class DiffElement<T> {
  public abstract String getPath();

  public abstract String getName();

  public abstract long getSize();

  public abstract long getModificationStamp();

  public abstract FileType getFileType();

  public abstract boolean isContainer();

  @Nullable
  public abstract DiffElement getParent();

  public abstract DiffElement<T>[] getChildren();

  @Nullable
  public abstract DiffElement<T> findFileByRelativePath(String path);

  /**
   * Returns content data as byte array. Can be null, if element for example is a container
   * @return content byte array
   */
  @Nullable
  public abstract byte[] getContent() throws IOException;

  public abstract boolean canCompareWith(DiffElement element);

  @Nullable
  public abstract JComponent getViewComponent(Project project);
  @Nullable
  public abstract JComponent getDiffComponent(DiffElement element, Project project, Window parentWindow);

  public abstract T getValue();

  public void disposeViewComponent() {}
  public void disposeDiffComponent() {}
}
