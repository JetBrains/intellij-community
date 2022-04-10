// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.schemes;

import com.intellij.configurationStore.SerializableScheme;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemeExporter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Exports (copies) a scheme to an external file as is.
 */
public class SerializableSchemeExporter extends SchemeExporter<Scheme> {
  @Override
  public void exportScheme(@Nullable Project project, @NotNull Scheme scheme, @NotNull OutputStream outputStream) throws Exception {
    exportScheme(scheme, outputStream);
  }

  /**
   * @deprecated Use {@link #exportScheme(Project, Scheme, OutputStream)}
   */
  @SuppressWarnings({"deprecation", "DeprecatedIsStillUsed"})
  @Override
  @Deprecated
  public void exportScheme(@NotNull Scheme scheme, @NotNull OutputStream outputStream) throws Exception {
    assert scheme instanceof SerializableScheme;
    writeToStream((SerializableScheme)scheme, outputStream);
  }

  public static void writeToStream(@NotNull SerializableScheme scheme, @NotNull OutputStream outputStream) throws IOException {
    writeToStream(outputStream, scheme.writeScheme());
  }

  @Override
  public String getExtension() {
    return "xml";
  }

  private static void writeToStream(@NotNull OutputStream outputStream, @NotNull Element element) throws IOException {
    JDOMUtil.write(element, outputStream);
  }
}
