// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.java.compiler;

import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author vladimir.dolzhenko
 */
public final class JpsJavaCompilerNotNullableSerializer extends JpsProjectExtensionSerializer {
  public JpsJavaCompilerNotNullableSerializer() {
    super("misc.xml", "NullableNotNullManager");
  }

  @Override
  public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project);
    List<String> annoNames = new ArrayList<>();
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
    JpsJavaExtensionService.getInstance().getCompilerConfiguration(project).setNotNullAnnotations(
      Collections.singletonList(NotNull.class.getName()));
  }
}
