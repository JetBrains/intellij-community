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

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

public class CodeStyleSettingsLoader {

  public CodeStyleSettings loadSettings(@NotNull VirtualFile file) throws SchemeImportException {
    Element rootElement = loadSchemeDom(file);
    CodeStyleSettings settings = new CodeStyleSettings();
    loadSettings(rootElement, settings);
    return settings;
  }

  @NotNull
  protected static Element loadSchemeDom(@NotNull VirtualFile file) throws SchemeImportException {
    InputStream inputStream = null;
    try {
      inputStream = file.getInputStream();
      final Document document = JDOMUtil.loadDocument(inputStream);
      final Element root = document.getRootElement();
      inputStream.close();
      return root;
    }
    catch (IOException e) {
      throw new SchemeImportException(getErrorMessage(e, file));
    }
    catch (JDOMException e) {
      throw new SchemeImportException(getErrorMessage(e, file));
    }
    finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        }
        catch (IOException e) {
          // ignore
        }
      }
    }
  }

  private static String getErrorMessage(@NotNull Exception e, @NotNull VirtualFile file) {
    return "Can't read from" + file.getName() + ", " + e.getMessage();
  }

  protected void loadSettings(@NotNull Element rootElement, @NotNull CodeStyleSettings settings) throws SchemeImportException {
    try {
      settings.readExternal(rootElement);
    }
    catch (InvalidDataException e) {
      throw new SchemeImportException(ApplicationBundle.message("settings.code.style.import.xml.error.can.not.load", e.getMessage()));
    }
  }

}
