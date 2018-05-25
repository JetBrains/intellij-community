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

import java.util.Arrays;
import java.util.List;

/**
 * @author vladimir.dolzhenko
 */
public class JpsJavaCompilerNotNullableSerializer extends JpsProjectExtensionSerializer {
  public static final List<String> DEFAULT_NOT_NULLS = Arrays.asList(
    NotNull.class.getName(),
    "javax.annotation.Nonnull",
    "javax.validation.constraints.NotNull",
    "edu.umd.cs.findbugs.annotations.NonNull",
    "android.support.annotation.NonNull",
    "androidx.annotation.NonNull",
    "org.checkerframework.checker.nullness.qual.NonNull",
    "org.checkerframework.checker.nullness.compatqual.NonNullDecl",
    "org.checkerframework.checker.nullness.compatqual.NonNullType"
  );

  public JpsJavaCompilerNotNullableSerializer() {
    super("misc.xml", "NullableNotNullManager");
  }

  @Override
  public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
    List<String> annoNames = ContainerUtil.newArrayList();
    for (Element option : componentTag.getChildren("option")) {
      if ("myNotNulls".equals(option.getAttributeValue("name"))){
        for (Element value : option.getChildren("value")) {
          for (Element list : value.getChildren("list")) {
            for (Element item : list.getChildren("item")) {
              ContainerUtil.addIfNotNull(annoNames, item.getAttributeValue("itemvalue"));
            }
          }
        }
      }
    }
    if (annoNames.isEmpty()) {
      annoNames.addAll(DEFAULT_NOT_NULLS);
    }
    configuration.setNotNullAnnotations(annoNames);
  }

  @Override
  public void loadExtensionWithDefaultSettings(@NotNull JpsProject project) {
    JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project).setNotNullAnnotations(DEFAULT_NOT_NULLS);
  }

  @Override
  public void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
  }
}
