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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.options.SchemeExporter;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * Exports (copies) a code style scheme to an external file as is.
 *
 * @author Rustam Vishnyakov
 */
public class CodeStyleSchemeCopyExporter extends SchemeExporter<CodeStyleScheme> {
  @Override
  public void exportScheme(@NotNull final CodeStyleScheme scheme, @NotNull OutputStream outputStream) throws Exception {
    assert scheme instanceof CodeStyleSchemeImpl;
    writeToStream(outputStream, schemeToDom((CodeStyleSchemeImpl)scheme));
  }

  @Override
  public String getExtension() {
    return "xml";
  }

  private static Element schemeToDom(@NotNull CodeStyleSchemeImpl scheme) throws WriteExternalException {
    Element newElement = new Element("code_scheme");
    newElement.setAttribute("name", scheme.getName());
    scheme.writeExternal(newElement);
    return newElement;
  }

  private static void writeToStream(@NotNull OutputStream outputStream, @NotNull Element element) throws IOException {
    OutputStreamWriter writer = new OutputStreamWriter(outputStream);
    Format format = Format.getPrettyFormat();
    format.setLineSeparator("\n");
    new XMLOutputter(format).output(element, writer);
  }
}
