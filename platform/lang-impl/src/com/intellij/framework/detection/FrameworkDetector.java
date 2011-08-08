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
package com.intellij.framework.detection;

import com.intellij.framework.FrameworkType;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public abstract class FrameworkDetector {
  public static final ExtensionPointName<FrameworkDetector> EP_NAME = ExtensionPointName.create("com.intellij.framework.detector");
  private final String myDetectorId;
  private final int myDetectorVersion;

  protected FrameworkDetector(String detectorId) {
    this(detectorId, 0);
  }

  protected FrameworkDetector(@NotNull String detectorId, int detectorVersion) {
    myDetectorId = detectorId;
    myDetectorVersion = detectorVersion;
  }

  @NotNull
  public abstract FileType getFileType();

  @NotNull
  public abstract ElementPattern<FileContent> createSuitableFilePattern();

  public abstract List<? extends DetectedFrameworkDescription> detect(@NotNull Collection<VirtualFile> newFiles,
                                                                      @NotNull FrameworkDetectionContext context);

  @NotNull
  public final String getDetectorId() {
    return myDetectorId;
  }

  public final int getDetectorVersion() {
    return myDetectorVersion;
  }

  public abstract FrameworkType getFrameworkType();
}
