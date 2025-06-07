// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.options;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * @author Eugene Zhuravlev
 */
public class JavaCompilersTab extends CompositeConfigurable<Configurable> implements SearchableConfigurable {
  private JavaCompilersTabUi myUi;

  private final Project myProject;
  private final CompilerConfigurationImpl myCompilerConfiguration;
  private final BackendCompiler myDefaultCompiler;
  private final Collection<BackendCompiler> myCompilers;
  private final List<Configurable> myConfigurables;
  private BackendCompiler mySelectedCompiler;

  public JavaCompilersTab(@NotNull Project project) {
    myProject = project;
    myCompilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    myDefaultCompiler = myCompilerConfiguration.getDefaultCompiler();

    myCompilers = myCompilerConfiguration.getRegisteredJavaCompilers();
    myConfigurables = new ArrayList<>(myCompilers.size());
  }

  @Override
  public String getDisplayName() {
    return JavaCompilerBundle.message("java.compiler.description");
  }

  @Override
  @SuppressWarnings("SpellCheckingInspection")
  public @NotNull String getHelpTopic() {
    return "reference.projectsettings.compiler.javacompiler";
  }

  @Override
  public @NotNull String getId() {
    return getHelpTopic();
  }

  @Override
  public JComponent createComponent() {
    myUi = new JavaCompilersTabUi(
      myProject,
      myCompilers,
      configurable -> myConfigurables.add(configurable),
      compiler -> selectCompiler(compiler)
    );
    return myUi.getPanel();
  }

  @Override
  public boolean isModified() {
    return !Comparing.equal(mySelectedCompiler, myCompilerConfiguration.getDefaultCompiler()) ||
           myUi.useReleaseOptionCb.isSelected() != myCompilerConfiguration.useReleaseOption() ||
           !Objects.equals(myUi.targetOptionsComponent.getProjectBytecodeTarget(), myCompilerConfiguration.getProjectBytecodeTarget()) ||
           !Comparing.equal(myUi.targetOptionsComponent.getModulesBytecodeTargetMap(), myCompilerConfiguration.getModulesBytecodeTargetMap()) ||
           super.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    try {
      myCompilerConfiguration.setDefaultCompiler(mySelectedCompiler);
      myCompilerConfiguration.setUseReleaseOption(myUi.useReleaseOptionCb.isSelected());
      myCompilerConfiguration.setProjectBytecodeTarget(myUi.targetOptionsComponent.getProjectBytecodeTarget());
      myCompilerConfiguration.setModulesBytecodeTargetMap(myUi.targetOptionsComponent.getModulesBytecodeTargetMap());

      super.apply();

      myUi.targetOptionsComponent.setProjectBytecodeTargetLevel(myCompilerConfiguration.getProjectBytecodeTarget());
      myUi.targetOptionsComponent.setModuleTargetLevels(myCompilerConfiguration.getModulesBytecodeTargetMap());
    }
    finally {
      if (!myProject.isDefault()) {
        BuildManager.getInstance().clearState(myProject);
      }
      PsiManager.getInstance(myProject).dropPsiCaches();
    }
  }

  @Override
  public void reset() {
    super.reset();
    selectCompiler(myCompilerConfiguration.getDefaultCompiler());
    myUi.useReleaseOptionCb.setSelected(myCompilerConfiguration.useReleaseOption());
    myUi.targetOptionsComponent.setProjectBytecodeTargetLevel(myCompilerConfiguration.getProjectBytecodeTarget());
    myUi.targetOptionsComponent.setModuleTargetLevels(myCompilerConfiguration.getModulesBytecodeTargetMap());
  }

  private void selectCompiler(BackendCompiler compiler) {
    if (compiler == null) {
      compiler = myDefaultCompiler;
    }
    myUi.compilerComboBox.setSelectedItem(compiler);
    mySelectedCompiler = compiler;
    myUi.show(compiler.getId());
  }

  @Override
  protected @NotNull List<Configurable> createConfigurables() {
    return myConfigurables;
  }

  @Override
  public void disposeUIResources() {
    myUi = null;
  }
}