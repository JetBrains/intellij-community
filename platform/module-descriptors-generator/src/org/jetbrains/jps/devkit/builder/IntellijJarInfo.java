/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.jps.devkit.builder;

import org.jetbrains.platform.loader.impl.repository.ModuleXmlParser;
import org.jetbrains.platform.loader.repository.RuntimeModuleDescriptor;
import org.jetbrains.platform.loader.repository.RuntimeModuleId;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author nik
 */
class IntellijJarInfo {
  private final List<RuntimeModuleDescriptor> myIncludedModules;
  private final Map<RuntimeModuleId, RuntimeModuleId> myDependencies;
  private final File myJarFile;

  public IntellijJarInfo(File jarFile) {
    myJarFile = jarFile;
    myIncludedModules = new ArrayList<RuntimeModuleDescriptor>();
    myDependencies = new LinkedHashMap<RuntimeModuleId, RuntimeModuleId>();
    try {
      JarFile jar = new JarFile(jarFile);
      try {
        Enumeration<JarEntry> entries = jar.entries();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        while (entries.hasMoreElements()) {
          JarEntry entry = entries.nextElement();
          String name = entry.getName();
          if (name.startsWith("META-INF/") && name.endsWith("-module.xml")) {
            RuntimeModuleDescriptor descriptor = ModuleXmlParser.parseModuleXml(factory, jar.getInputStream(entry), jarFile);
            myIncludedModules.add(descriptor);
            for (RuntimeModuleId dependency : descriptor.getDependencies()) {
              myDependencies.put(dependency, descriptor.getModuleId());
            }
          }
        }
      }
      catch (XMLStreamException e) {
        throw new IOException(e);
      }
      finally {
        jar.close();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public List<RuntimeModuleDescriptor> getIncludedModules() {
    return myIncludedModules;
  }

  /**
   * @return dependency id to id of module which requires it
   */
  public Map<RuntimeModuleId, RuntimeModuleId> getDependencies() {
    return myDependencies;
  }

  public File getJarFile() {
    return myJarFile;
  }
}
