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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
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
  public static final String PLATFORM_PLUGIN = "platform";
  private final List<RuntimeModuleDescriptorData> myIncludedModules;
  private final Map<RuntimeModuleId, RuntimeModuleId> myDependencies;
  private final File myJarFile;
  private final String myPluginName;

  public IntellijJarInfo(File jarFile, String pluginName) {
    myJarFile = jarFile;
    myPluginName = pluginName;
    myIncludedModules = new ArrayList<RuntimeModuleDescriptorData>();
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
            myIncludedModules.add(new RuntimeModuleDescriptorData(descriptor.getModuleId(), descriptor.getModuleRoots(),
                                                                  descriptor.getDependencies()));
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

  @NotNull
  static List<IntellijJarInfo> collectJarsFromDist(File distRoot) {
    final List<IntellijJarInfo> jars = new ArrayList<IntellijJarInfo>();
    collectJars(jars, new File(distRoot, "lib"), PLATFORM_PLUGIN);
    File[] pluginDirs = new File(distRoot, "plugins").listFiles();
    if (pluginDirs != null) {
      for (final File pluginDir : pluginDirs) {
        collectJars(jars, pluginDir, pluginDir.getName());
      }
    }
    return jars;
  }

  private static void collectJars(final List<IntellijJarInfo> jars, File jarRoot, final String pluginName) {
    FileUtil.processFilesRecursively(jarRoot, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (FileUtilRt.extensionEquals(file.getName(), "jar")) {
          jars.add(new IntellijJarInfo(file, pluginName));
        }
        return true;
      }
    });
  }

  public String getPluginName() {
    return myPluginName;
  }

  public List<RuntimeModuleDescriptorData> getIncludedModules() {
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
