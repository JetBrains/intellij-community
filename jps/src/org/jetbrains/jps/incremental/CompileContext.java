package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.incremental.storage.BuildDataManager;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public abstract class CompileContext implements MessageHandler, UserDataHolder {

  private final CompileScope myScope;
  private final boolean myIsMake;
  private final Map<Key, Object> myUserData = new ConcurrentHashMap<Key, Object>();
  private boolean myCompilingTests = false;
  private final BuildDataManager myDataManager;

  public CompileContext(CompileScope scope, String projectName, boolean isMake) {
    myScope = scope;
    myIsMake = isMake;
    final File buildDataRoot = new File(System.getProperty("user.home"), ".jps" + File.separator + projectName + File.separator + "build_data");
    myDataManager = new BuildDataManager(buildDataRoot);
  }

  public Project getProject() {
    return myScope.getProject();
  }

  public boolean isMake() {
    return myIsMake;
  }

  public boolean isCompilingTests() {
    return myCompilingTests;
  }

  public void setCompilingTests(boolean compilingTests) {
    myCompilingTests = compilingTests;
  }

  public CompileScope getScope() {
    return myScope;
  }

  public BuildDataManager getBuildDataManager() {
    return myDataManager;
  }

  public <T> T getUserData(@NotNull Key<T> key) {
    return (T)myUserData.get(key);
  }

  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myUserData.put(key, value);
  }

  public void processFiles(ModuleChunk chunk, FileProcessor processor) throws Exception {
    for (Module module : chunk.getModules()) {
      if (!processModule(module, processor)) {
        return;
      }
    }
  }

  /** @noinspection unchecked*/
  private boolean processModule(Module module, FileProcessor processor) throws Exception {
    final Set<File> excludes = new HashSet<File>();
    for (String excludePath : (Collection<String>)module.getExcludes()) {
      excludes.add(new File(excludePath));
    }

    final Collection<String> roots = myCompilingTests? (Collection<String>)module.getTestRoots() : (Collection<String>)module.getSourceRoots();
    for (String root : roots) {
      final File rootFile = new File(root);
      if (!processRootRecursively(module, rootFile, root, processor, excludes)) {
        return false;
      }
    }
    return true;
  }

  private static boolean processRootRecursively(final Module module, final File fromFile, final String sourceRoot, FileProcessor processor, final Set<File> excluded) throws Exception {
    if (fromFile.isDirectory()) {
      if (isExcluded(excluded, fromFile)) {
        return true;
      }
      final File[] children = fromFile.listFiles();
      if (children != null) {
        for (File child : children) {
          final boolean shouldContinue = processRootRecursively(module, child, sourceRoot, processor, excluded);
          if (!shouldContinue) {
            return false;
          }
        }
      }
      return true;
    }
    return processor.apply(module, fromFile, sourceRoot);
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
