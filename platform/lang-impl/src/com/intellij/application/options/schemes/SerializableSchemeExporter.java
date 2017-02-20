/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.application.options.schemes;

import com.intellij.configurationStore.SerializableScheme;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemeExporter;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * Exports (copies) a scheme to an external file as is.
 *
 * @author Rustam Vishnyakov
 */
public class SerializableSchemeExporter extends SchemeExporter<Scheme> {
  @Override
  public void exportScheme(@NotNull Scheme scheme, @NotNull OutputStream outputStream) throws Exception {
    assert scheme instanceof SerializableScheme;
    writeToStream(outputStream, ((SerializableScheme)scheme).writeScheme());
  }

  @Override
  public String getExtension() {
    return "xml";
  }

  private static void writeToStream(@NotNull OutputStream outputStream, @NotNull Element element) throws IOException {
    OutputStreamWriter writer = new OutputStreamWriter(outputStream);
    Format format = Format.getPrettyFormat();
    format.setLineSeparator("\n");
    new XMLOutputter(format).output(element, writer);
  }
}
