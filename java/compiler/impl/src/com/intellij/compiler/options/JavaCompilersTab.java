// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  private final JavaCompilersTabUi myUi;

  private final Project myProject;
  private final CompilerConfigurationImpl myCompilerConfiguration;
  private final BackendCompiler myDefaultCompiler;
  private final List<Configurable> myConfigurables;
  private BackendCompiler mySelectedCompiler;

  public JavaCompilersTab(@NotNull Project project) {
    myProject = project;
    myCompilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    myDefaultCompiler = myCompilerConfiguration.getDefaultCompiler();

    Collection<BackendCompiler> compilers = myCompilerConfiguration.getRegisteredJavaCompilers();
    myConfigurables = new ArrayList<>(compilers.size());

    myUi = new JavaCompilersTabUi(
      project,
      compilers,
      configurable -> myConfigurables.add(configurable),
      compiler -> selectCompiler(compiler)
    );
  }

  @Override
  public String getDisplayName() {
    return JavaCompilerBundle.message("java.compiler.description");
  }

  @NotNull
  @Override
  @SuppressWarnings("SpellCheckingInspection")
  public String getHelpTopic() {
    return "reference.projectsettings.compiler.javacompiler";
  }

  @NotNull
  @Override
  public String getId() {
    return getHelpTopic();
  }

  @Override
  public JComponent createComponent() {
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

  @NotNull
  @Override
  protected List<Configurable> createConfigurables() {
    return myConfigurables;
  }
}