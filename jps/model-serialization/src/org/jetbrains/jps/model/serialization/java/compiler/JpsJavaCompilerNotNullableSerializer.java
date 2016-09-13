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

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

/**
 * @author vladimir.dolzhenko
 */
public class JpsJavaCompilerNotNullableSerializer extends JpsProjectExtensionSerializer {
  private static final String DEFAULT_VALUE = NotNull.class.getName();
  private static final String NOTNULL_ANNOTATION = "myDefaultNotNull";
  private static final String VALUE = "value";

  public JpsJavaCompilerNotNullableSerializer() {
    super("misc.xml", "NullableNotNullManager");
  }

  @Override
  public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
    String value = DEFAULT_VALUE;
    for (Element element : componentTag.getChildren("option")) {
      if (NOTNULL_ANNOTATION.equals(element.getAttributeValue("name"))){
          value = element.getAttributeValue(VALUE, DEFAULT_VALUE);
          break;
      }
    }
    configuration.setNotNullAnnotation(value);
  }

  @Override
  public void loadExtensionWithDefaultSettings(@NotNull JpsProject project) {
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
    configuration.setNotNullAnnotation(DEFAULT_VALUE);
  }

  @Override
  public void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
  }
}
