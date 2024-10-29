// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

public class BuildElementsEditor extends ModuleElementsEditor {
  private final BuildElementsEditorUi myUi;

  protected BuildElementsEditor(final ModuleConfigurationState state) {
    super(state);

    myUi = new BuildElementsEditorUi(
      getModel().getModule(),
      this::enableCompilerSettings,
      this::commitCompilerOutputPath,
      this::commitTestsOutputPath,
      this::commitExcludeOutput
    );
  }

  @Override
  public JComponent createComponentImpl() {
    final boolean outputPathInherited = getCompilerExtension().isCompilerOutputPathInherited();
    myUi.inheritCompilerOutput.setSelected(outputPathInherited);
    myUi.perModuleCompilerOutput.setSelected(!outputPathInherited);
    myUi.excludeOutput.setSelected(getCompilerExtension().isExcludeOutput());

    // fill with data
    updateOutputPathPresentation();

    return myUi.getPanel();
  }

  private void fireModuleConfigurationChanged() {
    fireConfigurationChanged();
  }

  private void updateOutputPathPresentation() {
    if (getCompilerExtension().isCompilerOutputPathInherited()) {
      ProjectConfigurable projectConfig = ((ModulesConfigurator)getState().getModulesProvider()).getProjectStructureConfigurable().getProjectConfig();
      if (projectConfig == null) {
        return;
      }
      final String baseUrl = projectConfig.getCompilerOutputUrl();
      moduleCompileOutputChanged(baseUrl, getModel().getModule().getName());
    } else {
      final VirtualFile compilerOutputPath = getCompilerExtension().getCompilerOutputPath();
      if (compilerOutputPath != null) {
        myUi.compilerOutputPath.setText(FileUtil.toSystemDependentName(compilerOutputPath.getPath()));
      }
      else {
        final String compilerOutputUrl = getCompilerExtension().getCompilerOutputUrl();
        if (compilerOutputUrl != null) {
          myUi.compilerOutputPath.setText(FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(compilerOutputUrl)));
        }
      }
      final VirtualFile testsOutputPath = getCompilerExtension().getCompilerOutputPathForTests();
      if (testsOutputPath != null) {
        myUi.testCompilerOutputPath.setText(FileUtil.toSystemDependentName(testsOutputPath.getPath()));
      }
      else {
        final String testsOutputUrl = getCompilerExtension().getCompilerOutputUrlForTests();
        if (testsOutputUrl != null) {
          myUi.testCompilerOutputPath.setText(FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(testsOutputUrl)));
        }
      }
    }
  }

  @Nullable
  private static String resolveCanonicalPath(@NotNull String path) {
    if (path.isEmpty()) return null;

    String canonicalPath;
    try {
      canonicalPath = FileUtil.resolveShortWindowsName(path);
    }
    catch (IOException e) {
      canonicalPath = path;
    }

    return VfsUtilCore.pathToUrl(canonicalPath);
  }

  private void enableCompilerSettings(final boolean enabled) {
    getCompilerExtension().inheritCompilerOutputPath(!enabled);
    updateOutputPathPresentation();
    fireModuleConfigurationChanged();
  }

  private void commitCompilerOutputPath() {
    if (!getModel().isWritable()) return;
    final String path = myUi.compilerOutputPath.getText();
    getCompilerExtension().setCompilerOutputPath(resolveCanonicalPath(path));
    fireModuleConfigurationChanged();
  }

  private void commitTestsOutputPath() {
    if (!getModel().isWritable()) return;
    final String path = myUi.testCompilerOutputPath.getText();
    getCompilerExtension().setCompilerOutputPathForTests(resolveCanonicalPath(path));
    fireModuleConfigurationChanged();
  }

  private void commitExcludeOutput(boolean excludeOutput) {
    getCompilerExtension().setExcludeOutput(excludeOutput);
    fireModuleConfigurationChanged();
  }

  @Override
  public void saveData() {
    commitCompilerOutputPath();
    commitTestsOutputPath();
  }

  @Override
  public String getDisplayName() {
    return JavaUiBundle.message("output.tab.title");
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "project.structureModulesPage.outputJavadoc";
  }


  @Override
  public void moduleStateChanged() {
    //if content entries tree was changed
    myUi.excludeOutput.setSelected(getCompilerExtension().isExcludeOutput());
  }

  @Override
  public void moduleCompileOutputChanged(final String baseUrl, final String moduleName) {
    if (getCompilerExtension().isCompilerOutputPathInherited()) {
      if (baseUrl != null) {
        myUi.compilerOutputPath.setText(FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(baseUrl + "/" + CompilerModuleExtension
          .PRODUCTION + "/" + moduleName)));
        myUi.testCompilerOutputPath.setText(FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(baseUrl + "/" + CompilerModuleExtension
          .TEST + "/" + moduleName)));
      }
      else {
        myUi.compilerOutputPath.setText(null);
        myUi.testCompilerOutputPath.setText(null);
      }
    }
  }

  public CompilerModuleExtension getCompilerExtension() {
    return getModel().getModuleExtension(CompilerModuleExtension.class);
  }
}
