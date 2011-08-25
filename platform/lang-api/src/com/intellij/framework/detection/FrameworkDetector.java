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
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Override this class to provide automatic detection for a framework by analyzing files in the project. Use this extension only if the
 * framework requires some additional configuration which need to be performed by an user (e.g. specifying path to a SDK) and stored in
 * the project files. If support for the framework can be provided without user interaction it is preferable to enable framework specific
 * highlighting/completion and show framework related actions silently so this extension shouldn't be used in such cases.
 *
 * <p>
 * Frameworks detectors are used when a new project is created using 'Create Project from sources' option and when files are copied/created
 * in an opened project. In both cases a notification is shown to user allowing she to accept or ignore detected frameworks.
 * </p>
 * The implementation should be registered in your {@code plugin.xml}:
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&lt;framework.detector implementation="qualified-class-name"/&gt;
 * &lt;/extensions&gt;
 * </pre>
 *
 * @author nik
 */
public abstract class FrameworkDetector {
  public static final ExtensionPointName<FrameworkDetector> EP_NAME = ExtensionPointName.create("com.intellij.framework.detector");
  private final String myDetectorId;
  private final int myDetectorVersion;

  /**
   * @param detectorId the unique id for detector
   */
  protected FrameworkDetector(String detectorId) {
    this(detectorId, 0);
  }

  protected FrameworkDetector(@NotNull String detectorId, int detectorVersion) {
    myDetectorId = detectorId;
    myDetectorVersion = detectorVersion;
  }

  /**
   * @return type of files which are considered by the detector
   */
  @NotNull
  public abstract FileType getFileType();

  /**
   * Provides a filter for files which are specific for the frameworks. Use {@link FileContentPattern} class to create the filter.
   * @return filter for files
   */
  @NotNull
  public abstract ElementPattern<FileContent> createSuitableFilePattern();

  /**
   * This method is called when some files of type specified by {@link #getFileType()} and accepted by filter returned
   * by {@link #createSuitableFilePattern()} are found in the project.
   * @param newFiles files accepted by filter
   * @param context provides
   * @return list of detected framework descriptions (it may be empty)
   */
  public abstract List<? extends DetectedFrameworkDescription> detect(@NotNull Collection<VirtualFile> newFiles,
                                                                      @NotNull FrameworkDetectionContext context);

  /**
   * @return {@link FrameworkType} instance which will be used to present the framework in 'Frameworks Detected' dialog and 'Disable Detection' settings
   */
  public abstract FrameworkType getFrameworkType();

  /**
   * @return {@link FrameworkType} instance describing framework which is required for this framework.
   */
  @Nullable
  public FrameworkType getUnderlyingFrameworkType() {
    return null;
  }

  @NotNull
  public final String getDetectorId() {
    return myDetectorId;
  }

  public final int getDetectorVersion() {
    return myDetectorVersion;
  }
}
