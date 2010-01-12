/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.javadoc;

import com.intellij.CommonBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunnerRegistry;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.util.ExecutionErrorDialog;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class JavadocGenerationManager extends AbstractProjectComponent implements JDOMExternalizable {
  private final JavadocConfiguration myConfiguration;

  public static JavadocGenerationManager getInstance(Project project) {
    return project.getComponent(JavadocGenerationManager.class);
  }

  JavadocGenerationManager(Project project) {
    super(project);
    myConfiguration = new JavadocConfiguration(project);
  }

  public void generateJavadoc(final PsiDirectory directory, DataContext dataContext) {
    Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    final PsiPackage aPackage = directory != null ? JavaDirectoryService.getInstance().getPackage(directory) : null;
    String packageFQName = aPackage != null ? aPackage.getQualifiedName() : null;

    final GenerateJavadocDialog dialog = new GenerateJavadocDialog(packageFQName, myProject, myConfiguration);
    dialog.reset();
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    if (component != null) {
      dataContext = DataManager.getInstance().getDataContext(component);
    }
    else {
      dataContext = SimpleDataContext.getProjectContext(myProject);
    }

    if (dialog.isGenerationForPackage() && !dialog.isGenerationWithSubpackages()) {
      //remove package prefixes from javadoc.exe command
      final Module module = directory != null ? ModuleUtil.findModuleForPsiElement(directory) : null;
      if (module != null && packageFQName != null){
        boolean reset = false;
        final ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
        for (ContentEntry contentEntry : contentEntries) {
          final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
          for (SourceFolder sourceFolder : sourceFolders) {
            final String packagePrefix = sourceFolder.getPackagePrefix();
            final int prefixLength = packagePrefix.length();
            if (prefixLength > 0 && packageFQName.startsWith(packagePrefix) && packageFQName.length() > prefixLength){
              packageFQName = packageFQName.substring(prefixLength + 1);
              reset = true;
              break;
            }
            if (reset) break;
          }
        }
      }
    }

    myConfiguration.setGenerationOptions(new JavadocConfiguration.GenerationOptions(packageFQName, directory, dialog.isGenerationForPackage(),
                                                                                    dialog.isGenerationWithSubpackages()));
    try {
      final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(DefaultRunExecutor.EXECUTOR_ID, myConfiguration);
      assert runner != null;
      runner.execute(DefaultRunExecutor.getRunExecutorInstance(), new ExecutionEnvironment(myConfiguration, dataContext));
    }
    catch (ExecutionException e) {
      ExecutionErrorDialog.show(e, CommonBundle.getErrorTitle(), myProject);
    }
  }


  @NotNull
  public String getComponentName() {
    return "JavadocGenerationManager";
  }

  public void readExternal(Element element) throws InvalidDataException {
    myConfiguration.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myConfiguration.writeExternal(element);
  }

  public JavadocConfiguration getConfiguration() {
    return myConfiguration;
  }

}
