package org.jetbrains.jps.incremental;

import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class CompileContext {
  private final CompileScope myScope;

  public CompileContext(CompileScope scope) {
    myScope = scope;
  }


  public CompileScope getScope() {
    return myScope;
  }

  public boolean isDirty(File file) {
    return true; // todo
  }

  public void processFiles(ModuleChunk chunk, FileProcessor processor) {
    for (Module module : chunk.getModules()) {
      if (!processModule(module, processor)) {
        return;
      }
    }
  }

  private boolean processModule(Module module, FileProcessor processor) {
    final Set<File> excludes = new HashSet<File>();
    for (String excludePath : (Collection<String>)module.getExcludes()) {
      excludes.add(new File(excludePath));
    }

    for (String root : (Collection<String>)module.getSourceRoots()) {
      if (!processRootRecursively(module, new File(root), processor, excludes)) {
        return false;
      }
    }
    return true;
  }

  private static boolean processRootRecursively(final Module module, final File file, FileProcessor processor, final Set<File> excluded) {
    if (file.isDirectory()) {
      if (isExcluded(excluded, file)) {
        return true;
      }
      final File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          final boolean shouldContinue = processRootRecursively(module, child, processor, excluded);
          if (!shouldContinue) {
            return false;
          }
        }
      }
      return true;
    }
    return processor.apply(module, file);
  }

  private static boolean isExcluded(final Set<File> excludedRoots, File file) {
    while (file != null) {
      if (excludedRoots.contains(file)) {
        return true;
      }
      file = file.getParentFile();
    }
    return false;
  }

}
