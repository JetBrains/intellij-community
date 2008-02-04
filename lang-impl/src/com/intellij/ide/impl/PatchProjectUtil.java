/*
 * @author max
 */
package com.intellij.ide.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class PatchProjectUtil {
  private PatchProjectUtil() {
  }

  public static void patchProject(final Project project) {
    final Map<Pattern, Set<Pattern>> excludePatterns = loadPatterns("idea.exclude.patterns");
    final Map<Pattern, Set<Pattern>> includePatterns = loadPatterns("idea.include.patterns");

    if (excludePatterns.isEmpty() && includePatterns.isEmpty()) return;
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    final ModifiableModuleModel modulesModel = ModuleManager.getInstance(project).getModifiableModel();
    final Module[] modules = modulesModel.getModules();
    final ModifiableRootModel[] models = new ModifiableRootModel[modules.length];
    for (int i = 0; i < modules.length; i++) {
      models[i] = ModuleRootManager.getInstance(modules[i]).getModifiableModel();
      final int idx = i;
      final ContentEntry[] contentEntries = models[i].getContentEntries();
      for (final ContentEntry contentEntry : contentEntries) {
        final VirtualFile contentRoot = contentEntry.getFile();
        if (contentRoot == null) continue;
        final Set<VirtualFile> included = new HashSet<VirtualFile>();
        iterate(contentRoot, new ContentIterator() {
          public boolean processFile(final VirtualFile fileOrDir) {
            String relativeName = VfsUtil.getRelativePath(fileOrDir, contentRoot, '/');
            for (Pattern module : excludePatterns.keySet()) {
              if (module == null || module.matcher(modules[idx].getName()).matches()) {
                final Set<Pattern> dirPatterns = excludePatterns.get(module);
                for (Pattern pattern : dirPatterns) {
                  if (pattern.matcher(relativeName).matches()) {
                    contentEntry.addExcludeFolder(fileOrDir);
                    return false;
                  }
                }
              }
            }
            if (includePatterns.isEmpty()) return true;
            for (Pattern module : includePatterns.keySet()) {
              if (module == null || module.matcher(modules[idx].getName()).matches()) {
                final Set<Pattern> dirPatterns = includePatterns.get(module);
                for (Pattern pattern : dirPatterns) {
                  if (pattern.matcher(relativeName).matches()) {
                    included.add(fileOrDir);
                    return true;
                  }
                }
              }
            }
            return true;
          }
        }, index);
        processIncluded(contentEntry, included);
      }
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ProjectRootManagerEx.getInstanceEx(project).multiCommit(modulesModel, models);
      }
    });
  }

  public static void processIncluded(final ContentEntry contentEntry, final Set<VirtualFile> included) {
    if (included.isEmpty()) return;
    final Set<VirtualFile> parents = new HashSet<VirtualFile>();
    for (VirtualFile file : included) {
      if (file == contentEntry.getFile()) return;
      final VirtualFile parent = file.getParent();
      if (parent == null || parents.contains(parent)) continue;
      parents.add(parent);
      for (VirtualFile toExclude : parent.getChildren()) {
        boolean toExcludeSibling = true;
        for (VirtualFile includeRoot : included) {
          if (VfsUtil.isAncestor(toExclude, includeRoot, false)) {
            toExcludeSibling = false;
          }
        }
        if (toExcludeSibling) {
          contentEntry.addExcludeFolder(toExclude);
        }
      }
    }
    processIncluded(contentEntry, parents);
  }

  public static void iterate(VirtualFile contentRoot, ContentIterator iterator, ProjectFileIndex idx) {
    if (!iterator.processFile(contentRoot)) return;
    if (idx.getModuleForFile(contentRoot) == null) return; //already excluded
    final VirtualFile[] files = contentRoot.getChildren();
    for (VirtualFile file : files) {
      iterate(file, iterator, idx);
    }
  }

  public static Map<Pattern, Set<Pattern>> loadPatterns(@NonNls String propertyKey) {
    final Map<Pattern, Set<Pattern>> result = new HashMap<Pattern, Set<Pattern>>();
    final String patterns = System.getProperty(propertyKey);
    if (patterns != null) {
      final String[] pathPatterns = patterns.split(";");
      for (String excludedPattern : pathPatterns) {
        String module = null;
        int idx = 0;
        if (excludedPattern.startsWith("[")) {
          idx = excludedPattern.indexOf("]") + 1;
          module = excludedPattern.substring(1, idx - 1);
        }
        final Pattern modulePattern = module != null ? Pattern.compile(module) : null;
        final Pattern pattern = Pattern.compile(FileUtil.convertAntToRegexp(excludedPattern.substring(idx)));
        Set<Pattern> dirPatterns = result.get(modulePattern);
        if (dirPatterns == null) {
          dirPatterns = new HashSet<Pattern>();
          result.put(modulePattern, dirPatterns);
        }
        dirPatterns.add(pattern);
      }
    }
    return result;
  }
}