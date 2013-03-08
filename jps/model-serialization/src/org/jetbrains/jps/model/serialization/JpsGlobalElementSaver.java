/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.jps.model.JpsGlobal;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public class JpsGlobalElementSaver {
  private static final JpsGlobalExtensionSerializer[] SERIALIZERS = {
    new JpsGlobalLoader.PathVariablesSerializer(), new JpsGlobalLoader.GlobalLibrariesSerializer(), new JpsGlobalLoader.SdkTableSerializer()
  };
  private final JpsGlobal myGlobal;

  public JpsGlobalElementSaver(JpsGlobal global) {
    myGlobal = global;
  }

  public static void saveGlobalElement(JpsGlobal global, String optionsPath) throws IOException {
    File optionsDir = new File(FileUtil.toCanonicalPath(optionsPath));
    new JpsGlobalElementSaver(global).save(optionsDir);
  }

  private void save(File optionsDir) throws IOException {
    for (JpsGlobalExtensionSerializer serializer : SERIALIZERS) {
      saveGlobalComponents(serializer, optionsDir);
    }
  }

  private void saveGlobalComponents(JpsGlobalExtensionSerializer serializer, File optionsDir) throws IOException {
    String fileName = serializer.getConfigFileName();
    File configFile = new File(optionsDir, fileName != null ? fileName : "other.xml");
    Element rootElement = loadOrCreateRootElement(configFile);
    serializer.saveExtension(myGlobal, JDomSerializationUtil.findOrCreateComponentElement(rootElement, serializer.getComponentName()));
    JDOMUtil.writeDocument(new Document(rootElement), configFile, SystemProperties.getLineSeparator());
  }

  private static Element loadOrCreateRootElement(File configFile) {
    if (!configFile.exists()) {
      return new Element("application");
    }
    try {
      return JDOMUtil.loadDocument(configFile).getRootElement();
    }
    catch (JDOMException e) {
      throw new RuntimeException(e);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
