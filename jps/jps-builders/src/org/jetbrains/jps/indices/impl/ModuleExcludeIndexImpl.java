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
package org.jetbrains.jps.indices.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;
import org.jetbrains.jps.model.java.JpsJavaProjectExtension;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/11/12
 */
public class ModuleExcludeIndexImpl implements ModuleExcludeIndex {
  private final Set<File> myExcludedRoots = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
  private final Set<File> myContentRoots = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
  private final Map<JpsModule, List<File>> myModuleToExcludesMap = new THashMap<JpsModule, List<File>>();

  public ModuleExcludeIndexImpl(JpsModel model) {
    final Collection<JpsModule> allModules = model.getProject().getModules();
    for (final JpsModule module : allModules) {
      final List<File> moduleExcludes = new ArrayList<File>();
      for (String url : module.getExcludeRootsList().getUrls()) {
        moduleExcludes.add(JpsPathUtil.urlToFile(url));
      }
      JpsJavaModuleExtension moduleExtension = JpsJavaExtensionService.getInstance().getModuleExtension(module);
      if (moduleExtension != null && !moduleExtension.isInheritOutput() && moduleExtension.isExcludeOutput()) {
        String outputUrl = moduleExtension.getOutputUrl();
        if (outputUrl != null) {
          moduleExcludes.add(JpsPathUtil.urlToFile(outputUrl));
        }
        String testOutputUrl = moduleExtension.getTestOutputUrl();
        if (testOutputUrl != null) {
          moduleExcludes.add(JpsPathUtil.urlToFile(testOutputUrl));
        }
      }
      myModuleToExcludesMap.put(module, moduleExcludes);
      myExcludedRoots.addAll(moduleExcludes);
    }

    Map<File, JpsModule> contentToModule = new THashMap<File, JpsModule>(FileUtil.FILE_HASHING_STRATEGY);
    for (JpsModule module : allModules) {
      for (String contentUrl : module.getContentRootsList().getUrls()) {
        File contentRoot = JpsPathUtil.urlToFile(contentUrl);
        contentToModule.put(contentRoot, module);
      }
    }
    List<File> parents = new ArrayList<File>();
    for (JpsModule module : allModules) {
      for (String contentUrl : module.getContentRootsList().getUrls()) {
        File contentRoot = JpsPathUtil.urlToFile(contentUrl);
        File parent = contentRoot.getParentFile();
        JpsModule parentModule = null;
        parents.clear();
        while (parent != null) {
          parents.add(parent);
          if (contentToModule.containsKey(parent)) {
            parentModule = contentToModule.get(parent);
            break;
          }
          parent = parent.getParentFile();
        }
        if (parentModule != null) {
          myModuleToExcludesMap.get(parentModule).add(contentRoot);
        }
        else {
          myContentRoots.add(contentRoot);
        }
        for (File file : parents) {
          contentToModule.put(file, parentModule);
        }
      }
    }

    JpsJavaProjectExtension projectExtension = JpsJavaExtensionService.getInstance().getProjectExtension(model.getProject());
    if (projectExtension != null) {
      String url = projectExtension.getOutputUrl();
      if (!StringUtil.isEmpty(url)) {
        File excluded = JpsPathUtil.urlToFile(url);
        File parent = excluded;
        while (parent != null) {
          JpsModule module = contentToModule.get(parent);
          if (module != null) {
            myModuleToExcludesMap.get(module).add(excluded);
          }
          parent = FileUtil.getParentFile(parent);
        }
        myExcludedRoots.add(excluded);
      }
    }

    for (List<File> files : myModuleToExcludesMap.values()) {
      ((ArrayList<File>)files).trimToSize();
    }
  }

  @Override
  public boolean isExcluded(File file) {
    return JpsPathUtil.isUnder(myExcludedRoots, file);
  }

  @Override
  public boolean isInContent(File file) {
    return JpsPathUtil.isUnder(myContentRoots, file);
  }

  @Override
  public Collection<File> getModuleExcludes(JpsModule module) {
    return myModuleToExcludesMap.get(module);
  }
}
