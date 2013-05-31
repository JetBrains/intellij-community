/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.jps.TimingLog;
import org.jetbrains.jps.model.JpsElement;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public abstract class JpsLoaderBase {
  private final JpsMacroExpander myMacroExpander;

  protected JpsLoaderBase(JpsMacroExpander macroExpander) {
    myMacroExpander = macroExpander;
  }

  protected Element loadRootElement(final File file) {
    return loadRootElement(file, myMacroExpander);
  }

  protected <E extends JpsElement> void loadComponents(File dir,
                                                       final String defaultFileName,
                                                       JpsElementExtensionSerializerBase<E> serializer,
                                                       final E element) {
    String fileName = serializer.getConfigFileName();
    File configFile = new File(dir, fileName != null ? fileName : defaultFileName);
    Runnable timingLog = TimingLog.startActivity("loading: " + configFile.getName() + ":" + serializer.getComponentName());
    Element componentTag;
    if (configFile.exists()) {
      componentTag = JDomSerializationUtil.findComponent(loadRootElement(configFile), serializer.getComponentName());
    }
    else {
      componentTag = null;
    }

    if (componentTag != null) {
      serializer.loadExtension(element, componentTag);
    }
    else {
      serializer.loadExtensionWithDefaultSettings(element);
    }
    timingLog.run();
  }

  protected static Element loadRootElement(final File file, final JpsMacroExpander macroExpander) {
    try {
      final Element element = JDOMUtil.loadDocument(file).getRootElement();
      macroExpander.substitute(element, SystemInfo.isFileSystemCaseSensitive);
      return element;
    }
    catch (JDOMException e) {
      throw new RuntimeException("Cannot parse xml file " + file.getAbsolutePath() + ": " + e.getMessage(), e);
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot read file " + file.getAbsolutePath() + ": " + e.getMessage(), e);
    }
  }

  protected static boolean isXmlFile(File file) {
    return file.isFile() && FileUtilRt.extensionEquals(file.getName(), "xml");
  }
}
