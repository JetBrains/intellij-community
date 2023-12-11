// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.compiler;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.options.OptionControllerProvider;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaCompilerConfigurationProxy;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public final class JavaCompilerConfiguration extends JavaCompilerConfigurationProxy {
  @Override
  @NotNull
  public List<String> getAdditionalOptionsImpl(@NotNull Project project, @NotNull Module module) {
    return CompilerConfiguration.getInstance(project).getAdditionalOptions(module);
  }

  @Override
  public void setAdditionalOptionsImpl(@NotNull Project project, @NotNull Module module, @NotNull List<String> options) {
    CompilerConfiguration.getInstance(project).setAdditionalOptions(module, options);
  }

  /**
   * Provides an option "JavaCompilerConfiguration.additionalOptions" to change module-specific compiler options 
   */
  public static final class Provider implements OptionControllerProvider {

    @SuppressWarnings({"InjectedReferences", "LanguageMismatch"})
    @Override
    public @NotNull OptionController forContext(@NotNull PsiElement context) {
      Module module = Objects.requireNonNull(ModuleUtil.findModuleForFile(context.getContainingFile()));
      Project project = module.getProject();
      String bindId = "additionalOptions";
      return OptionController.empty()
        .onValue(bindId,
                 () -> getAdditionalOptions(project, module),
                 value -> {
                   setAdditionalOptions(project, module, value);
                   PsiManager.getInstance(project).dropPsiCaches();
                   DaemonCodeAnalyzer.getInstance(project).restart();
                 })
        .withRootPane(() -> OptPane.pane(OptPane.stringList(bindId, JavaCompilerBundle.message("settings.override.compilation.options.column"))));
    }

    @Override
    public @NotNull String name() {
      return "JavaCompilerConfiguration";
    }
  }
}
