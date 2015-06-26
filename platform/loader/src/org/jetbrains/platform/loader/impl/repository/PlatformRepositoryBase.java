/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.platform.loader.impl.repository;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.platform.loader.PlatformLoaderException;
import org.jetbrains.platform.loader.repository.PlatformRepository;
import org.jetbrains.platform.loader.repository.RuntimeModuleDescriptor;
import org.jetbrains.platform.loader.repository.RuntimeModuleId;

import javax.xml.stream.XMLInputFactory;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.jetbrains.platform.loader.impl.repository.RepositoryConstants.MODULES_ZIP_NAME;
import static org.jetbrains.platform.loader.impl.repository.RepositoryConstants.MODULE_DESCRIPTORS_DIR_NAME;

/**
 * @author nik
 */
public abstract class PlatformRepositoryBase implements PlatformRepository {
  private final Map<RuntimeModuleId, RuntimeModuleDescriptor> myModulesCache = ContainerUtil.newConcurrentMap();

  @Nullable
  @Override
  public RuntimeModuleDescriptor getModule(@NotNull RuntimeModuleId moduleName) {
    RuntimeModuleDescriptor module = myModulesCache.get(moduleName);
    if (module == null) {
      module = findModule(moduleName);
      if (module != null) {
        myModulesCache.put(moduleName, module);
      }
    }
    return module;
  }

  protected static Map<RuntimeModuleId, RuntimeModuleDescriptor> loadModulesFromZip(File outputRoot) {
    Map<RuntimeModuleId, RuntimeModuleDescriptor> map = new HashMap<RuntimeModuleId, RuntimeModuleDescriptor>();
    try {
      File descriptorsOutputRoot = new File(outputRoot, MODULE_DESCRIPTORS_DIR_NAME);
      File modulesZip = new File(descriptorsOutputRoot, MODULES_ZIP_NAME);
      if (!modulesZip.exists()) {
        throw new PlatformLoaderException(MODULES_ZIP_NAME + " file is not found in " + descriptorsOutputRoot);
      }

      ZipInputStream input = new ZipInputStream(new BufferedInputStream(new FileInputStream(modulesZip)));
      try {
        ZipEntry entry;
        XMLInputFactory factory = XMLInputFactory.newInstance();
        while ((entry = input.getNextEntry()) != null) {
          String name = entry.getName();
          if (name.endsWith("module.xml")) {
            RuntimeModuleDescriptor descriptor = ModuleXmlParser.parseModuleXml(factory, input, descriptorsOutputRoot);
            map.put(descriptor.getModuleId(), descriptor);
          }
        }
      }
      finally {
        input.close();
      }
    }
    catch (Exception e) {
      throw new PlatformLoaderException("Failed to load module descriptors from " + outputRoot.getAbsolutePath(), e);
    }
    return map;
  }

  @Nullable
  protected abstract RuntimeModuleDescriptor findModule(RuntimeModuleId moduleName);

  @Override
  @NotNull
  public RuntimeModuleDescriptor getRequiredModule(@NotNull RuntimeModuleId moduleName) {
    RuntimeModuleDescriptor dependency = getModule(moduleName);
    if (dependency == null) {
      throw new PlatformLoaderException("Cannot find module '" + moduleName.getStringId() + "' in " + this);
    }
    return dependency;
  }

  @NotNull
  @Override
  public List<String> getModuleRootPaths(@NotNull RuntimeModuleId id) {
    List<String> paths = new ArrayList<String>();
    for (File file : getRequiredModule(id).getModuleRoots()) {
      paths.add(file.getAbsolutePath());
    }
    return paths;
  }
}
