// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard.importSources;

import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Override this class to provide automatic detection of modules and libraries for 'Create from existing sources' mode of
 * the new project/module wizard.
 *
 * <p>
 * The implementation should be registered in your {@code plugin.xml}:
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&lt;projectStructureDetector implementation="qualified-class-name"/&gt;
 * &lt;/extensions&gt;
 * </pre>
 */
public abstract class ProjectStructureDetector {
  public static final ExtensionPointName<ProjectStructureDetector> EP_NAME = ExtensionPointName.create("com.intellij.projectStructureDetector");

  /**
   * This methods is called recursively for all directories under the selected root directory.
   * @param dir current directory
   * @param children its children
   * @param base the root directory in which project files are detected. All detected roots must be located under this directory.
   * @param result list of detected roots
   * @return
   *   <li>{@link DirectoryProcessingResult#PROCESS_CHILDREN} if children of {@code dir} need to be processed by this detector</li>
   *   <li>{@link DirectoryProcessingResult#SKIP_CHILDREN} to skip all children of {@code dir} from processing</li>
   *   <li><code>{@link DirectoryProcessingResult#skipChildrenAndParentsUpTo}(parent)</code> to skip all directories under {@code parent} from processing.
   *   {@code parent} must be an ancestor of {@code dir} or {@code dir} itself
   *   </li>
   */
  @NotNull
  public abstract DirectoryProcessingResult detectRoots(@NotNull File dir, File @NotNull [] children, @NotNull File base,
                                                        @NotNull List<DetectedProjectRoot> result);

  /**
   * Return additional wizard steps which will be shown if some roots are detected by this detector
   */
  public List<ModuleWizardStep> createWizardSteps(ProjectFromSourcesBuilder builder, ProjectDescriptor projectDescriptor, Icon stepIcon) {
    return Collections.emptyList();
  }

  public String getDetectorId() {
    return getClass().getName();
  }

  /**
   * Setup modules and libraries for the selected roots
   */
  public void setupProjectStructure(@NotNull Collection<DetectedProjectRoot> roots, @NotNull ProjectDescriptor projectDescriptor,
                                    @NotNull ProjectFromSourcesBuilder builder) {
  }

  public static final class DirectoryProcessingResult {
    private final boolean myProcessChildren;
    private final File myParentToSkip;
    public static final DirectoryProcessingResult PROCESS_CHILDREN = new DirectoryProcessingResult(true, null);
    public static final DirectoryProcessingResult SKIP_CHILDREN = new DirectoryProcessingResult(false, null);

    public static DirectoryProcessingResult skipChildrenAndParentsUpTo(@NotNull File parent) {
      return new DirectoryProcessingResult(false, parent);
    }

    private DirectoryProcessingResult(boolean processChildren, File parentToSkip) {
      myProcessChildren = processChildren;
      myParentToSkip = parentToSkip;
    }

    public boolean isProcessChildren() {
      return myProcessChildren;
    }

    @Nullable
    public File getParentToSkip() {
      return myParentToSkip;
    }
  }
}
