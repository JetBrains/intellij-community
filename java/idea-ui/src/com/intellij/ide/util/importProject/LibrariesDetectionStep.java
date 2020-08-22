// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.importProject;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.projectWizard.AbstractStepWithProgress;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder;
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector;
import com.intellij.openapi.fileTypes.FileTypeManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class LibrariesDetectionStep extends AbstractStepWithProgress<List<LibraryDescriptor>> {
  private final ProjectFromSourcesBuilder myBuilder;
  private final ProjectDescriptor myProjectDescriptor;
  private final ModuleInsight myInsight;
  private final Icon myIcon;
  private final String myHelpId;
  private LibrariesLayoutPanel myLibrariesPanel;

  public LibrariesDetectionStep(ProjectFromSourcesBuilder builder,
                                ProjectDescriptor projectDescriptor, final ModuleInsight insight,
                                Icon icon,
                                @NonNls String helpId) {
    super(JavaUiBundle.message("library.detection.dialog.message.stop.library.analysis"));
    myBuilder = builder;
    myProjectDescriptor = projectDescriptor;
    myInsight = insight;
    myIcon = icon;
    myHelpId = helpId;
  }

  @Override
  public void updateDataModel() {
    myProjectDescriptor.setLibraries(myLibrariesPanel.getChosenEntries());
  }

  @Override
  protected JComponent createResultsPanel() {
    myLibrariesPanel = new LibrariesLayoutPanel(myInsight);
    return myLibrariesPanel;
  }

  @Override
  protected String getProgressText() {
    return JavaUiBundle.message("progress.text.searching.for.libraries");
  }

  private int myPreviousStateHashCode = -1;
  @Override
  protected boolean shouldRunProgress() {
    final int currentHash = calcStateHashCode();
    try {
      return currentHash != myPreviousStateHashCode;
    }
    finally {
      myPreviousStateHashCode = currentHash;
    }
  }

  private int calcStateHashCode() {
    int hash = myBuilder.getBaseProjectPath().hashCode();
    for (DetectedSourceRoot root : getSourceRoots(myInsight, myBuilder)) {
      hash = 31 * hash + root.getDirectory().hashCode();
    }
    return hash;
  }

  @Nullable
  public static List<LibraryDescriptor> calculate(@NotNull ModuleInsight insight, @NotNull ProjectFromSourcesBuilder builder) {
    final List<DetectedSourceRoot> sourceRoots = getSourceRoots(insight, builder);

    final HashSet<String> ignored = new HashSet<>();
    final StringTokenizer tokenizer = new StringTokenizer(FileTypeManager.getInstance().getIgnoredFilesList(), ";", false);
    while (tokenizer.hasMoreTokens()) {
      ignored.add(tokenizer.nextToken());
    }

    insight.setRoots(Collections.singletonList(new File(builder.getBaseProjectPath())), sourceRoots, ignored);
    insight.scanLibraries();

    return insight.getSuggestedLibraries();
  }

  @Override
  protected List<LibraryDescriptor> calculate() {
    return calculate(myInsight, myBuilder);
  }

  @NotNull
  private static List<DetectedSourceRoot> getSourceRoots(@NotNull ModuleInsight insight, @NotNull ProjectFromSourcesBuilder builder) {
    final List<DetectedSourceRoot> sourceRoots = new ArrayList<>();
    for (ProjectStructureDetector detector : ProjectStructureDetector.EP_NAME.getExtensions()) {
      for (DetectedProjectRoot root : builder.getProjectRoots(detector)) {
        if (insight.isApplicableRoot(root)) {
          sourceRoots.add((DetectedSourceRoot)root);
        }
      }
    }
    return sourceRoots;
  }

  @Override
  protected void onFinished(List<LibraryDescriptor> libraries, final boolean canceled) {
    myLibrariesPanel.rebuild();
  }

  @Override
  public Icon getIcon() {
    return myIcon;
  }

  @Override
  public String getHelpId() {
    return myHelpId;
  }

}