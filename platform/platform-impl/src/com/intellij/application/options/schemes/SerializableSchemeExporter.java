// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.schemes;

import com.intellij.configurationStore.SerializableScheme;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemeExporter;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Exports (copies) a scheme to an external file as is.
 *
 * @author Rustam Vishnyakov
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
    OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
    Format format = Format.getPrettyFormat();
    format.setLineSeparator("\n");
    new XMLOutputter(format).output(element, writer);
  }
}
