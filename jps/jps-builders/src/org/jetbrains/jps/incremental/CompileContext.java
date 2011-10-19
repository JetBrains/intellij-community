package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.containers.SLRUCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class CompileContext extends UserDataHolderBase implements MessageHandler{

  private final CompileScope myScope;
  private final boolean myIsMake;
  private final MessageHandler myDelegateMessageHandler;
  private volatile boolean myCompilingTests = false;
  private final BuildDataManager myDataManager;
  private final Mappings myMappings;

  private SLRUCache<Module, FSSnapshot> myFilesCache = new SLRUCache<Module, FSSnapshot>(10, 10) {
    @NotNull
    public FSSnapshot createValue(Module key) {
      return buildSnapshot(key);
    }
  };

  public CompileContext(CompileScope scope, String projectName, boolean isMake, MessageHandler delegateMessageHandler) {
    myScope = scope;
    myIsMake = isMake;
    myDelegateMessageHandler = delegateMessageHandler;
    final File buildDataRoot = new File(System.getProperty("user.home"), ".jps" + File.separator + projectName + File.separator + "build_data");
    myDataManager = new BuildDataManager(buildDataRoot);
    myMappings = new Mappings();
  }

  public Project getProject() {
    return myScope.getProject();
  }

  public boolean isMake() {
    return myIsMake;
  }

  public Mappings getMappings() {
    return myMappings;
  }

  public boolean isCompilingTests() {
    return myCompilingTests;
  }

  public void setCompilingTests(boolean compilingTests) {
    myCompilingTests = compilingTests;
    clearFileCache();
  }

  public void clearFileCache() {
    myFilesCache.clear();
  }

  public CompileScope getScope() {
    return myScope;
  }

  public BuildDataManager getBuildDataManager() {
    return myDataManager;
  }

  public void processMessage(BuildMessage msg) {
    myDelegateMessageHandler.processMessage(msg);
  }

  public void processFiles(ModuleChunk chunk, FileProcessor processor) throws Exception {
    for (Module module : chunk.getModules()) {
      final FSSnapshot snapshot = myFilesCache.get(module);
      if (!snapshot.processFiles(processor)) {
        return;
      }
    }
  }

  /** @noinspection unchecked*/
  //private boolean processModule(Module module, FileProcessor processor) throws Exception {
  //  final Set<File> excludes = new HashSet<File>();
  //  for (String excludePath : (Collection<String>)module.getExcludes()) {
  //    excludes.add(new File(excludePath));
  //  }
  //
  //  final Collection<String> roots = myCompilingTests? (Collection<String>)module.getTestRoots() : (Collection<String>)module.getSourceRoots();
  //  for (String root : roots) {
  //    final File rootFile = new File(root);
  //    if (!processRootRecursively(module, rootFile, root, processor, excludes)) {
  //      return false;
  //    }
  //  }
  //  return true;
  //}

  private FSSnapshot buildSnapshot(Module module) {
    final Set<File> excludes = new HashSet<File>();
    for (String excludePath : (Collection<String>)module.getExcludes()) {
      excludes.add(new File(excludePath));
    }
    final FSSnapshot snapshot = new FSSnapshot(module);
    final Collection<String> roots = myCompilingTests? (Collection<String>)module.getTestRoots() : (Collection<String>)module.getSourceRoots();
    for (String srcRoot : roots) {
      final FSSnapshot.Root root = snapshot.addRoot(new File(srcRoot), srcRoot);
      buildStructure(root.getNode(), excludes);
    }
    return snapshot;
  }

  //private static boolean processRootRecursively(final Module module, final File fromFile, final String sourceRoot, FileProcessor processor, final Set<File> excluded) throws Exception {
  //  if (fromFile.isDirectory()) {
  //    if (isExcluded(excluded, fromFile)) {
  //      return true;
  //    }
  //    final File[] children = fromFile.listFiles();
  //    if (children != null) {
  //      for (File child : children) {
  //        final boolean shouldContinue = processRootRecursively(module, child, sourceRoot, processor, excluded);
  //        if (!shouldContinue) {
  //          return false;
  //        }
  //      }
  //    }
  //    return true;
  //  }
  //  return processor.apply(module, fromFile, sourceRoot);
  //}

  private static void buildStructure(final FSSnapshot.Node from, final Set<File> excluded) {
    final File nodeFile = from.getFile();
    if (nodeFile.isDirectory()) {
      if (isExcluded(excluded, nodeFile)) {
        return;
      }
      final File[] children = nodeFile.listFiles();
      if (children != null) {
        for (File child : children) {
          buildStructure(from.addChild(child), excluded);
        }
      }
    }
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
