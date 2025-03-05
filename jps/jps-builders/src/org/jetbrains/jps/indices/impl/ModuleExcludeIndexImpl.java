// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.indices.impl;

import com.intellij.openapi.fileTypes.impl.FileTypeAssocTable;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
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
import java.io.FileFilter;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class ModuleExcludeIndexImpl implements ModuleExcludeIndex {
  private final Set<Path> myExcludedRoots = FileCollectionFactory.createCanonicalPathSet();
  private final Set<Path> myTopLevelContentRoots = FileCollectionFactory.createCanonicalPathSet();
  private final Map<JpsModule, ArrayList<Path>> myModuleToExcludesMap = new HashMap<>();
  private final Map<JpsModule, List<Path>> myModuleToContentMap = new HashMap<>();
  private final Map<Path, FileTypeAssocTable<Boolean>> myExcludeFromContentRootTables = FileCollectionFactory.createCanonicalPathMap();

  public ModuleExcludeIndexImpl(JpsModel model) {
    final Collection<JpsModule> allModules = model.getProject().getModules();
    Map<Path, JpsModule> contentToModule = FileCollectionFactory.createCanonicalPathMap();

    /* maps URL of content root to URLs of its source roots if there are exclusion patterns to be reused */
    MultiMap<String, String> contentToSourceRootsWithExcludePatterns = MultiMap.createLinked();

    MultiMap<String, String> excludePatterns = MultiMap.createLinked();
    for (final JpsModule module : allModules) {
      ArrayList<Path> moduleExcludes = new ArrayList<>();
      for (String url : module.getExcludeRootsList().getUrls()) {
        moduleExcludes.add(Path.of(JpsPathUtil.urlToPath(url)));
      }
      JpsJavaModuleExtension moduleExtension = JpsJavaExtensionService.getInstance().getModuleExtension(module);
      if (moduleExtension != null && !moduleExtension.isInheritOutput() && moduleExtension.isExcludeOutput()) {
        String outputUrl = moduleExtension.getOutputUrl();
        if (outputUrl != null) {
          moduleExcludes.add(Path.of(JpsPathUtil.urlToPath(outputUrl)));
        }
        String testOutputUrl = moduleExtension.getTestOutputUrl();
        if (testOutputUrl != null) {
          moduleExcludes.add(Path.of(JpsPathUtil.urlToPath(testOutputUrl)));
        }
      }
      List<JpsExcludePattern> excludePatternsList = module.getExcludePatterns();
      for (JpsExcludePattern pattern : excludePatternsList) {
        excludePatterns.putValue(pattern.getBaseDirUrl(), pattern.getPattern());
      }
      List<String> contentUrls = module.getContentRootsList().getUrls();
      List<Path> moduleContent = new ArrayList<>(contentUrls.size());
      for (String contentUrl : contentUrls) {
        Path contentRoot = Path.of(JpsPathUtil.urlToPath(contentUrl));
        moduleContent.add(contentRoot);
        contentToModule.put(contentRoot, module);
      }
      for (JpsModuleSourceRoot root : module.getSourceRoots()) {
        Path sourceRoot = root.getPath();
        moduleContent.add(sourceRoot);
        contentToModule.put(sourceRoot, module);
        //source root should reuse exclusion patterns from its parent content root if any
        for (JpsExcludePattern pattern : excludePatternsList) {
          if (FileUtil.isAncestor(JpsPathUtil.urlToPath(pattern.getBaseDirUrl()), sourceRoot.toString(), true)) {
            contentToSourceRootsWithExcludePatterns.putValue(pattern.getBaseDirUrl(), root.getUrl());
          }
        }
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
      myExcludeFromContentRootTables.put(Path.of(JpsPathUtil.urlToPath(entry.getKey())), table);
      Collection<String> sourceRootUrls = contentToSourceRootsWithExcludePatterns.get(entry.getKey());
      for (String sourceRootUrl : sourceRootUrls) {
        myExcludeFromContentRootTables.put(Path.of(JpsPathUtil.urlToPath(sourceRootUrl)), table);
      }
    }

    JpsJavaProjectExtension projectExtension = JpsJavaExtensionService.getInstance().getProjectExtension(model.getProject());
    if (projectExtension != null) {
      String url = projectExtension.getOutputUrl();
      if (!Strings.isEmpty(url)) {
        Path excluded = Path.of(JpsPathUtil.urlToPath(url));
        Path parent = excluded;
        while (parent != null) {
          JpsModule module = contentToModule.get(parent);
          if (module != null) {
            myModuleToExcludesMap.get(module).add(excluded);
          }
          parent = parent.getParent();
        }
        myExcludedRoots.add(excluded);
      }
    }

    List<Path> parents = new ArrayList<>();
    Set<Path> notUnderExcludedCache = FileCollectionFactory.createCanonicalPathSet();
    for (JpsModule module : allModules) {
      for (Path contentRoot : myModuleToContentMap.get(module)) {
        Path parent = contentRoot.getParent();
        JpsModule parentModule = null;
        parents.clear();
        while (parent != null) {
          parents.add(parent);
          if (contentToModule.containsKey(parent)) {
            parentModule = contentToModule.get(parent);
            break;
          }
          parent = parent.getParent();
        }

        if (parentModule != null) {
          if (!parentModule.equals(module)) {
            myModuleToExcludesMap.get(parentModule).add(contentRoot);
          }
          // if the content root is located under an excluded root,
          // we need to register it as top-level root to ensure that 'isExcluded' works correctly
          if (isUnderExcluded(contentRoot, myExcludedRoots, notUnderExcludedCache)) {
            myTopLevelContentRoots.add(contentRoot);
          }
        }
        else {
          myTopLevelContentRoots.add(contentRoot);
        }
        for (Path file : parents) {
          contentToModule.put(file, parentModule);
        }
      }
    }

    for (ArrayList<Path> files : myModuleToExcludesMap.values()) {
      files.trimToSize();
    }
  }

  private static boolean isUnderExcluded(Path root, Set<? extends Path> excluded, Set<? super Path> notUnderExcludedCache) {
    Path parent = root;
    List<Path> parents = new ArrayList<>();
    while (parent != null) {
      if (notUnderExcludedCache.contains(parent)) {
        return false;
      }
      if (excluded.contains(parent)) {
        return true;
      }
      parents.add(parent);
      parent = parent.getParent();
    }
    notUnderExcludedCache.addAll(parents);
    return false;
  }

  @Override
  public boolean isExcluded(File file) {
    return determineFileLocation(file.toPath(), myTopLevelContentRoots, myExcludedRoots) == FileLocation.EXCLUDED;
  }

  @Override
  public boolean isExcludedFromModule(@NotNull File file, @NotNull JpsModule module) {
    return determineFileLocation(file.toPath(), myModuleToContentMap.get(module), myModuleToExcludesMap.get(module)) == FileLocation.EXCLUDED;
  }

  @Override
  public boolean isInContent(@NotNull File file) {
    return determineFileLocation(file.toPath(), myTopLevelContentRoots, myExcludedRoots) == FileLocation.IN_CONTENT;
  }

  private enum FileLocation { IN_CONTENT, EXCLUDED, NOT_IN_PROJECT }

  private FileLocation determineFileLocation(Path file, Collection<Path> roots, Collection<Path> excluded) {
    if (roots.isEmpty() && excluded.isEmpty()) {
      return FileLocation.NOT_IN_PROJECT; // optimization
    }
    Path current = file;
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
      current = current.getParent();
    }
    return FileLocation.NOT_IN_PROJECT;
  }

  private static boolean isExcludedByPattern(Path file, Path root, FileTypeAssocTable<Boolean> table) {
    Path current = file;
    // it's ok to compare files by 'equals' here be because these files are produced by the same 'getParentFile' calls
    while (current != null && !current.equals(root)) {
      if (table.findAssociatedFileType(current.getFileName().toString()) != null) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  @Override
  public @Unmodifiable @NotNull Collection<@NotNull Path> getModuleExcludes(@NotNull JpsModule module) {
    return myModuleToExcludesMap.get(module);
  }

  @Override
  public @NotNull FileFilter getModuleFileFilterHonorExclusionPatterns(@NotNull JpsModule module) {
    List<Path> contentRoots = myModuleToContentMap.get(module);
    if (contentRoots == null || contentRoots.isEmpty() || myExcludeFromContentRootTables.isEmpty()) {
      return FileFilters.EVERYTHING;
    }
    return file -> determineFileLocation(file.toPath(), contentRoots, Collections.emptyList()) == FileLocation.IN_CONTENT;
  }
}
