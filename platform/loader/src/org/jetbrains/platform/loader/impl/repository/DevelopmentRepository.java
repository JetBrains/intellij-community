/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.platform.loader.impl.repository;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.platform.loader.impl.ModuleDescriptorsGenerationRunner;
import org.jetbrains.platform.loader.repository.RuntimeModuleDescriptor;
import org.jetbrains.platform.loader.repository.RuntimeModuleId;

import java.io.File;
import java.util.Map;

/**
 * @author nik
 */
public class DevelopmentRepository extends PlatformRepositoryBase {
  private final File myOutputRoot;
  private final Map<RuntimeModuleId, RuntimeModuleDescriptor> myLibraryModules;

  /**
   * @param projectHome IDEA project directory
   * @param outputRoot project compiler output root for IntelliJ project
   */
  public DevelopmentRepository(@NotNull File projectHome, @NotNull File outputRoot) {
    if (Boolean.parseBoolean(System.getProperty(RepositoryConstants.CHECK_DEVELOPMENT_REPOSITORY_UP_TO_DATE_PROPERTY, "true"))) {
      ModuleDescriptorsGenerationRunner.runGenerator(projectHome, outputRoot, null);
    }
    myOutputRoot = outputRoot;
    myLibraryModules = loadModulesFromZip(outputRoot);
  }

  @Override
  @Nullable
  protected RuntimeModuleDescriptor findModule(RuntimeModuleId moduleName) {
    String name = moduleName.getStringId();
    RuntimeModuleDescriptor libraryModule = myLibraryModules.get(moduleName);
    if (libraryModule != null) {
      return libraryModule;
    }
    File root;
    if (name.endsWith(RuntimeModuleId.TESTS_NAME_SUFFIX)) {
      root = new File(myOutputRoot, "test/" + StringUtil.trimEnd(name, RuntimeModuleId.TESTS_NAME_SUFFIX));
    }
    else {
      root = new File(myOutputRoot, "production/" + name);
    }
    if (root.isDirectory()) {
      return ModuleXmlParser.loadFromFile(root, moduleName);
    }
    return null;
  }

  @Override
  public String toString() {
    return "Development Repository [" + myOutputRoot.getAbsolutePath() + "]";
  }
}
