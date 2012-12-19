/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.model.serialization.java.compiler;

import com.intellij.openapi.util.JDOMExternalizerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

/**
 * @author nik
 */
public class JpsJavaCompilerWorkspaceConfigurationSerializer extends JpsProjectExtensionSerializer {
  public JpsJavaCompilerWorkspaceConfigurationSerializer() {
    super(WORKSPACE_FILE, "CompilerWorkspaceConfiguration");
  }

  @Override
  public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
    String assertNotNull = JDOMExternalizerUtil.readField(componentTag, "ASSERT_NOT_NULL");
    if (assertNotNull != null) {
      configuration.setAddNotNullAssertions(Boolean.parseBoolean(assertNotNull));
    }
    String clearOutputDirectory = JDOMExternalizerUtil.readField(componentTag, "CLEAR_OUTPUT_DIRECTORY");
    configuration.setClearOutputDirectoryOnRebuild(clearOutputDirectory == null || Boolean.parseBoolean(clearOutputDirectory));
  }

  @Override
  public void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
  }
}
