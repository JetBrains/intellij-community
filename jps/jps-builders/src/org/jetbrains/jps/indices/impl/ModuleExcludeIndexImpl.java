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

import com.intellij.openapi.fileTypes.impl.FileTypeAssocTable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsExcludePattern;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;
import org.jetbrains.jps.model.java.JpsJavaProjectExtension;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class ModuleExcludeIndexImpl implements ModuleExcludeIndex {
  private final Set<File> myExcludedRoots = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
  private final Set<File> myTopLevelContentRoots = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
  private final Map<JpsModule, ArrayList<File>> myModuleToExcludesMap = new THashMap<>();
  private final Map<JpsModule, List<File>> myModuleToContentMap = new THashMap<>();
  private final Map<File, FileTypeAssocTable<Boolean>> myExcludeFromContentRootTables = new THashMap<>(FileUtil.FILE_HASHING_STRATEGY);

  public ModuleExcludeIndexImpl(JpsModel model) {
    final Collection<JpsModule> allModules = model.getProject().getModules();
    Map<File, JpsModule> contentToModule = new THashMap<>(FileUtil.FILE_HASHING_STRATEGY);
    MultiMap<String, String> excludePatterns = MultiMap.createLinked();
    for (final JpsModule module : allModules) {
      final ArrayList<File> moduleExcludes = new ArrayList<>();
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
      for (JpsExcludePattern pattern : module.getExcludePatterns()) {
        excludePatterns.putValue(pattern.getBaseDirUrl(), pattern.getPattern());
      }
      List<String> contentUrls = module.getContentRootsList().getUrls();
      final List<File> moduleContent = new ArrayList<>(contentUrls.size());
      for (String contentUrl : contentUrls) {
        File contentRoot = JpsPathUtil.urlToFile(contentUrl);
        moduleContent.add(contentRoot);
        contentToModule.put(contentRoot, module);
      }
      for (JpsModuleSourceRoot root : module.getSourceRoots()) {
        File sourceRoot = root.getFile();
        moduleContent.add(sourceRoot);
        contentToModule.put(sourceRoot, module);
      }
      myModuleToExcludesMap.put(module, moduleExcludes);
      myModuleToContentMap.put(module, moduleContent);
      myExcludedRoots.addAll(moduleExcludes);
    }

    FileNameMatcherFactory factory = FileNameMatcherFactory.getInstance();
    for (Map.Entry<String, Collection<String>> entry : excludePatterns.entrySet()) {
      FileTypeAssocTable<Boolean> table = new FileTypeAssocTable<>();
      for (String pattern : entry.getValue()) {
        table.addAssociation(factory.createMatcher(pattern), Boolean.TRUE);
      }
      myExcludeFromContentRootTables.put(JpsPathUtil.urlToFile(entry.getKey()), table);
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

    List<File> parents = new ArrayList<>();
    Set<File> notUnderExcludedCache = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
    for (JpsModule module : allModules) {
      for (File contentRoot : myModuleToContentMap.get(module)) {
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
          if (!parentModule.equals(module)) {
            myModuleToExcludesMap.get(parentModule).add(contentRoot);
          }
          //if the content root is located under an excluded root we need to register it as top-level root to ensure that 'isExcluded' works correctly
          if (isUnderExcluded(contentRoot, myExcludedRoots, notUnderExcludedCache)) {
            myTopLevelContentRoots.add(contentRoot);
          }
        }
        else {
          myTopLevelContentRoots.add(contentRoot);
        }
        for (File file : parents) {
          contentToModule.put(file, parentModule);
        }
      }
    }

    for (List<File> files : myModuleToExcludesMap.values()) {
      ((ArrayList<File>)files).trimToSize();
    }
  }

  private static boolean isUnderExcluded(File root, Set<File> excluded, Set<File> notUnderExcludedCache) {
    File parent = root;
    List<File> parents = new ArrayList<>();
    while (parent != null) {
      if (notUnderExcludedCache.contains(parent)) {
        return false;
      }
      if (excluded.contains(parent)) {
        return true;
      }
      parents.add(parent);
      parent = parent.getParentFile();
    }
    notUnderExcludedCache.addAll(parents);
    return false;
  }

  @Override
  public boolean isExcluded(File file) {
    return determineFileLocation(file, myTopLevelContentRoots, myExcludedRoots) == FileLocation.EXCLUDED;
  }

  @Override
  public boolean isExcludedFromModule(File file, JpsModule module) {
    return determineFileLocation(file, myModuleToContentMap.get(module), myModuleToExcludesMap.get(module)) == FileLocation.EXCLUDED;
  }

  @Override
  public boolean isInContent(File file) {
    return determineFileLocation(file, myTopLevelContentRoots, myExcludedRoots) == FileLocation.IN_CONTENT;
  }

  private enum FileLocation { IN_CONTENT, EXCLUDED, NOT_IN_PROJECT }

  private FileLocation determineFileLocation(File file, Collection<File> roots, Collection<File> excluded) {
    if (roots.isEmpty() && excluded.isEmpty()) {
      return FileLocation.NOT_IN_PROJECT; // optimization
    }
    File current = file;
    while (current != null) {
      if (excluded.contains(current)) {
        return FileLocation.EXCLUDED;
      }
      FileTypeAssocTable<Boolean> table = myExcludeFromContentRootTables.get(current);
      if (table != null && isExcludedByPattern(file, current, table)) {
        return FileLocation.EXCLUDED;
      }
      if (roots.contains(current)) {
        return FileLocation.IN_CONTENT;
      }
      current = FileUtilRt.getParentFile(current);
    }
    return FileLocation.NOT_IN_PROJECT;
  }

  private static boolean isExcludedByPattern(File file, File root, FileTypeAssocTable<Boolean> table) {
    File current = file;
    //noinspection FileEqualsUsage it's ok to compare files by 'equals' here be because these files are produced by the same 'getParentFile' calls
    while (current != null && !current.equals(root)) {
      if (table.findAssociatedFileType(current.getName()) != null) {
        return true;
      }
      current = FileUtilRt.getParentFile(current);
    }
    return false;
  }

  @Override
  public Collection<File> getModuleExcludes(JpsModule module) {
    return myModuleToExcludesMap.get(module);
  }
}
