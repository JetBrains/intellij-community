/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ProjectRootsTraversing {
  public static final RootTraversePolicy FULL_CLASSPATH =
    new RootTraversePolicy(RootTraversePolicy.ALL_OUTPUTS, RootTraversePolicy.ADD_CLASSES, RootTraversePolicy.ADD_CLASSES, null);

  public static final RootTraversePolicy LIBRARIES_AND_JDK =
    new RootTraversePolicy(null, RootTraversePolicy.ADD_CLASSES, RootTraversePolicy.ADD_CLASSES, null);

  public static final RootTraversePolicy PROJECT_SOURCES =
    new RootTraversePolicy(RootTraversePolicy.SOURCES, null, null, null);

  public static final RootTraversePolicy FULL_CLASSPATH_RECURSIVE =
    new RootTraversePolicy(RootTraversePolicy.ALL_OUTPUTS, RootTraversePolicy.ADD_CLASSES, RootTraversePolicy.ADD_CLASSES, RootTraversePolicy.RECURSIVE);

  public static final RootTraversePolicy FULL_CLASS_RECURSIVE_WO_JDK =
    new RootTraversePolicy(RootTraversePolicy.ALL_OUTPUTS, null, RootTraversePolicy.ADD_CLASSES, RootTraversePolicy.RECURSIVE);

  public static final RootTraversePolicy FULL_CLASSPATH_WITHOUT_TESTS =
    new RootTraversePolicy(RootTraversePolicy.GENERAL_OUTPUT, RootTraversePolicy.ADD_CLASSES, RootTraversePolicy.ADD_CLASSES, RootTraversePolicy.RECURSIVE);

  public static final RootTraversePolicy PROJECT_LIBRARIES =
    new RootTraversePolicy(null, null, RootTraversePolicy.ADD_CLASSES, RootTraversePolicy.RECURSIVE);

  public static PathsList collectRoots(Project project, RootTraversePolicy policy) {
    PathsList listBuilder = new PathsList();
    collectRoots(project, policy, listBuilder);
    return listBuilder;
  }

  public static void collectRoots(final Project project, final RootTraversePolicy policy, final PathsList listBuilder) {
    traverseOrder(project, policy, new TraverseState(listBuilder));
  }

  public static PathsList collectRoots(Module module, RootTraversePolicy policy) {
    final PathsList listBuilder = new PathsList();
    collectRoots(module, policy, listBuilder);
    return listBuilder;
  }

  public static void collectRoots(Module module, RootTraversePolicy policy, PathsList listBuilder) {
    traverseOrder(module, policy, new TraverseState(listBuilder));
  }

  private static void traverseOrder(Project project, RootPolicy<TraverseState> policy, TraverseState state) {
    final Module[] sortedModules = ModuleManager.getInstance(project).getSortedModules();
    for (Module sortedModule : sortedModules) {
      traverseOrder(sortedModule, policy, state);
    }
  }

  private static void traverseOrder(Module module, RootPolicy<TraverseState> policy, TraverseState state) {
    if (!state.beforeVisitModule(module)) {
      return;
    }
    state.getCurrentModuleManager().processOrder(policy, state);
  }

  private static class TraverseState implements UserDataHolder {
    private final UserDataHolderBase myUserData = new UserDataHolderBase();
    private final PathsList myCollectedPath;
    private final HashSet<Module> myKnownModules = new HashSet<Module>();
    private ModuleRootManager myCurrentModuleManager;

    private TraverseState(PathsList listBuilder) {
      myCollectedPath = listBuilder;
    }

    public PathsList getCollectedPath() {
      return myCollectedPath;
    }

    public boolean beforeVisitModule(Module module) {
      if (myKnownModules.contains(module)) {
        return false;
      }
      myKnownModules.add(module);
      myCurrentModuleManager = ModuleRootManager.getInstance(module);
      return true;
    }

    public void addAll(VirtualFile[] items) {
      for (VirtualFile item : items) {
        add(item);
      }
    }

    private void add(VirtualFile item) {
      if (item != null && item.isValid()) {
        myCollectedPath.add(item);
      }
    }

    private void addUrl(String url) {
      if (url != null) {
        myCollectedPath.add(PathUtil.toPresentableUrl(url));
      }
    }

    public ModuleRootManager getCurrentModuleManager() {
      return myCurrentModuleManager;
    }

    public void restorCurrentModuleManager(ModuleRootManager restored) {
      myCurrentModuleManager = restored;
    }

    public <T> T getUserData(Key<T> key) {
      return myUserData.getUserData(key);
    }

    public <T> void putUserData(Key<T> key, T value) {
      myUserData.putUserData(key, value);
    }

    public void addAllUrls(String[] urls) {
      for (String url : urls) addUrl(url);
    }

    public void addAllUrls(List<String> urls) {
      for (String url : urls) {
        addUrl(url);
      }
    }
  }

  public static class RootTraversePolicy extends RootPolicy<TraverseState> {
    private static final Key<Boolean> JDK_PROCESSED = Key.create("jdkProcessed");
    private final Visit<ModuleSourceOrderEntry> myVisitSource;
    private final Visit<OrderEntry> myVisitJdk;
    private final Visit<OrderEntry> myVisitLibrary;
    private final Visit<ModuleOrderEntry> myVisitModule;

    public RootTraversePolicy(Visit<ModuleSourceOrderEntry> visitSource, Visit<OrderEntry> visitJdk, Visit<OrderEntry> visitLibrary, Visit<ModuleOrderEntry> visitModule) {
      myVisitSource = visitSource;
      myVisitJdk = visitJdk;
      myVisitLibrary = visitLibrary;
      myVisitModule = visitModule;
    }

    public TraverseState visitJdkOrderEntry(JdkOrderEntry jdkOrderEntry, TraverseState state) {
      Boolean jdkProcessed = state.getUserData(JDK_PROCESSED);
      if (jdkProcessed != null && jdkProcessed.booleanValue()) return state;
      state.putUserData(JDK_PROCESSED, Boolean.TRUE);
      if (myVisitJdk != null) myVisitJdk.visit(jdkOrderEntry, state, this);
      return state;
    }

    public TraverseState visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, TraverseState traverseState) {
      if (myVisitLibrary != null) myVisitLibrary.visit(libraryOrderEntry, traverseState, this);
      return traverseState;
    }

    public TraverseState visitModuleSourceOrderEntry(ModuleSourceOrderEntry sourceEntry,
                                                     TraverseState traverseState) {
      if (myVisitSource != null) myVisitSource.visit(sourceEntry, traverseState, this);
      return traverseState;
    }

    public TraverseState visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, TraverseState traverseState) {
      if (myVisitModule != null) myVisitModule.visit(moduleOrderEntry, traverseState, this);
      return traverseState;
    }

    private interface Visit<T extends OrderEntry> {
      void visit(T entry, TraverseState state, RootPolicy<TraverseState> policy);
    }

    public static final AddModuleOutput ALL_OUTPUTS = new AddModuleOutput(true);
    public static final AddModuleOutput GENERAL_OUTPUT = new AddModuleOutput(false);
    public static final AddModuleSource SOURCES = new AddModuleSource();
    public static final AddModuleSource PRODUCTION_SOURCES = new AddModuleSource(true);

    public static final Visit<OrderEntry> ADD_CLASSES = new Visit<OrderEntry>() {
      public void visit(OrderEntry orderEntry, TraverseState state, RootPolicy<TraverseState> policy) {
        state.addAllUrls(orderEntry.getUrls(OrderRootType.CLASSES));
      }
    };

    public static final Visit<ModuleOrderEntry> RECURSIVE = new Visit<ModuleOrderEntry>() {
      public void visit(ModuleOrderEntry moduleOrderEntry, TraverseState state, RootPolicy<TraverseState> policy) {
        Module module = moduleOrderEntry.getModule();
        if (module == null) return;
        ModuleRootManager moduleRootManager = state.getCurrentModuleManager();
        traverseOrder(module, policy, state);
        state.restorCurrentModuleManager(moduleRootManager);
      }
    };

    public static class AddModuleOutput implements Visit<ModuleSourceOrderEntry> {
      private final boolean myIncludeTests;

      public AddModuleOutput(boolean includeTests) {
        myIncludeTests = includeTests;
      }

      public void visit(ModuleSourceOrderEntry sourceEntry, TraverseState traverseState, RootPolicy<TraverseState> policy) {
        traverseState.addAllUrls(getOutputs(traverseState.getCurrentModuleManager().getModule()));
      }

      public List<String> getOutputs(Module module) {
        ArrayList<String> outputs = new ArrayList<String>();
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        String testOutput = moduleRootManager.getCompilerOutputPathForTestsUrl();
        if (myIncludeTests && testOutput != null) outputs.add(testOutput);
        String output = moduleRootManager.getCompilerOutputPathUrl();
        if ((!Comparing.equal(output, testOutput) || !myIncludeTests) && output != null) {
          outputs.add(output);
        }
        return outputs;
      }
    }

    public static class AddModuleSource implements Visit<ModuleSourceOrderEntry> {
      private boolean myExcludeTests;

      public AddModuleSource() {
        this(false);
      }

      public AddModuleSource(final boolean excludeTests) {
        myExcludeTests = excludeTests;
      }

      public void visit(ModuleSourceOrderEntry orderEntry, TraverseState state, RootPolicy<TraverseState> policy) {
        if (myExcludeTests) {
          ContentEntry[] contentEntries = ModuleRootManager.getInstance(orderEntry.getOwnerModule()).getContentEntries();
          for (ContentEntry contentEntry : contentEntries) {
            for (SourceFolder folder : contentEntry.getSourceFolders()) {
              VirtualFile root = folder.getFile();
              if (root != null && !folder.isTestSource()) {
                state.add(root);
              }
            }
          }
        }
        else {
          state.addAll(orderEntry.getFiles(OrderRootType.SOURCES));
        }
      }
    }
  }
}
