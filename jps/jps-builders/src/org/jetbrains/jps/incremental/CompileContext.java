package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.SLRUCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ether.dependencyView.ClassRepr;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class CompileContext extends UserDataHolderBase implements MessageHandler{

  private final CompileScope myScope;
  private final boolean myIsMake;
  private final ProjectChunks myProductionChunks;
  private final ProjectChunks myTestChunks;
  private final MessageHandler myDelegateMessageHandler;
  private volatile boolean myCompilingTests = false;
  private final BuildDataManager myDataManager;
  private final Mappings myMappings;
  private final Set<Module> myDirtyModules = new HashSet<Module>();
  private final Map<Module, Collection<File>> myTempSourceRoots = new HashMap<Module, Collection<File>>();

  private SLRUCache<Module, FSSnapshot> myFilesCache = new SLRUCache<Module, FSSnapshot>(10, 10) {
    @NotNull
    public FSSnapshot createValue(Module key) {
      return buildSnapshot(key);
    }
  };
  private final ProjectPaths myProjectPaths;

  public CompileContext(CompileScope scope,
                        String projectName,
                        boolean isMake,
                        final Mappings mappings,
                        ProjectChunks productionChunks,
                        ProjectChunks testChunks,
                        MessageHandler delegateMessageHandler) {
    myScope = scope;
    myIsMake = isMake;
    myProductionChunks = productionChunks;
    myTestChunks = testChunks;
    myDelegateMessageHandler = delegateMessageHandler;
    myDataManager = new BuildDataManager(projectName);
    myMappings = mappings;
    myProjectPaths = new ProjectPaths(scope.getProject());
  }

  public Project getProject() {
    return myScope.getProject();
  }

  public ProjectPaths getProjectPaths() {
    return myProjectPaths;
  }

  public boolean isMake() {
    return myIsMake;
  }

  public boolean isDirty(ModuleChunk chunk) {
    for (Module module : chunk.getModules()) {
      if (isDirty(module)) {
        return true;
      }
    }
    return false;
  }

  public boolean isDirty(Module module) {
    return myDirtyModules.contains(module);
  }

  public void setDirty(ModuleChunk chunk, boolean isDirty) {
    final Set<Module> modules = chunk.getModules();
    if (isDirty) {
      myDirtyModules.addAll(modules);
    }
    else {
      myDirtyModules.removeAll(modules);
    }

    // now mark all modules that depend on dirty modules
    final ClasspathKind classpathKind = ClasspathKind.compile(isCompilingTests());
    final ProjectChunks chunks = isCompilingTests()? myTestChunks : myProductionChunks;
    boolean found = false;
    for (ModuleChunk moduleChunk : chunks.getChunkList()) {
      if (!found) {
        if (moduleChunk.equals(chunk)) {
          found = true;
        }
      }
      else {
        MODULES_LOOP: for (final Module module : moduleChunk.getModules()) {
          for (ClasspathItem dependency : module.getClasspath(classpathKind)) {
            if (dependency instanceof Module && isDirty((Module)dependency)) {
              myDirtyModules.addAll(moduleChunk.getModules());
              break MODULES_LOOP;
            }
          }
        }
      }
    }
  }

  public Mappings getMappings() {
    return myMappings;
  }
  
  public Mappings createDelta() {
    return myMappings.createDelta();
  }

  public boolean isCompilingTests() {
    return myCompilingTests;
  }

  void setCompilingTests(boolean compilingTests) {
    myCompilingTests = compilingTests;
    myFilesCache.clear();
    for (Collection<File> roots : myTempSourceRoots.values()) {
      if (roots != null) {
        for (File root : roots) {
          FileUtil.delete(root);
        }
      }
    }
    myTempSourceRoots.clear();
  }

  void onChunkBuildComplete(@NotNull ModuleChunk chunk) {
    myFilesCache.clear();
    for (Module module : chunk.getModules()) {
      final Collection<File> roots = myTempSourceRoots.remove(module);
      if (roots != null) {
        for (File root : roots) {
          FileUtil.delete(root);
        }
      }
    }
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

  public boolean hasRemovedSources() {
    final Set<File> removed = Paths.CHUNK_REMOVED_SOURCES_KEY.get(this);
    return removed != null && !removed.isEmpty();
  }

  // delete all class files that according to mappings correspond to given sources
  public void deleteCorrespondingClasses(Collection<File> sources) {
    if (isMake() && !sources.isEmpty()) {
      final Mappings mappings = getMappings();
      for (File file : sources) {
        final Set<ClassRepr> classes = mappings.getClasses(FileUtil.toSystemIndependentName(file.getPath()));
        if (classes != null) {
          for (ClassRepr aClass : classes) {
            final String fileName = aClass.getFileName();
            if (fileName != null) {
              FileUtil.delete(new File(fileName));
            }
          }
        }
      }
    }
  }

  // assuming the root file exists
  public void registerTempSourceRoot(Module module, File root) {
    Collection<File> roots = myTempSourceRoots.get(module);
    if (roots == null) {
      roots = new HashSet<File>();
      myTempSourceRoots.put(module, roots);
    }
    roots.add(root);
  }


  /** @noinspection unchecked*/
  private FSSnapshot buildSnapshot(Module module) {
    final Set<File> excludes = new HashSet<File>();
    for (String excludePath : (Collection<String>)module.getExcludes()) {
      excludes.add(new File(excludePath));
    }
    final FSSnapshot snapshot = new FSSnapshot(module);
    final Collection<String> roots = myCompilingTests? (Collection<String>)module.getTestRoots() : (Collection<String>)module.getSourceRoots();
    for (String srcRoot : roots) {
      final File rootFile = new File(srcRoot);
      if (rootFile.exists()) {
        final FSSnapshot.Root root = snapshot.addRoot(rootFile, srcRoot);
        buildStructure(root.getNode(), excludes);
      }
    }
    final Collection<File> tempRoots = myTempSourceRoots.get(module);
    if (tempRoots != null) {
      for (File tempRoot : tempRoots) {
        final FSSnapshot.Root root = snapshot.addRoot(tempRoot, FileUtil.toSystemIndependentName(tempRoot.getPath()));
        buildStructure(root.getNode(), excludes);
      }
    }
    return snapshot;
  }

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
