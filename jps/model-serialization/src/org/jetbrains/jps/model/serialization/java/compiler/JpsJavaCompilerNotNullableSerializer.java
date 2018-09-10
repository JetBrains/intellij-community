/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

import java.util.Collections;
import java.util.List;

/**
 * @author vladimir.dolzhenko
 */
public class JpsJavaCompilerNotNullableSerializer extends JpsProjectExtensionSerializer {

  public JpsJavaCompilerNotNullableSerializer() {
    super("misc.xml", "NullableNotNullManager");
  }

  @Override
  public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
    List<String> annoNames = ContainerUtil.newArrayList();
    for (Element option : componentTag.getChildren("instrumentedNotNulls")) {
      for (Element item : option.getChildren("option")) {
        ContainerUtil.addIfNotNull(annoNames, item.getAttributeValue("value"));
      }
    }
    if (annoNames.isEmpty()) {
      annoNames.add(NotNull.class.getName());
    }
    configuration.setNotNullAnnotations(annoNames);
  }

  @Override
  public void loadExtensionWithDefaultSettings(@NotNull JpsProject project) {
    JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project).setNotNullAnnotations(
      Collections.singletonList(NotNull.class.getName()));
  }

  @Override
  public void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
  }
}
