/*
 * @author: Eugene Zhuravlev
 * Date: Jan 21, 2003
 * Time: 4:19:03 PM
 */
package com.intellij.compiler.impl;

import com.intellij.compiler.CompilerMessageImpl;
import com.intellij.compiler.make.DependencyCache;
import com.intellij.compiler.progress.CompilerProgressIndicator;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.compiler.ex.CompilerPathsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.pom.Navigatable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;


public class CompileContextImpl extends UserDataHolderBase implements CompileContextEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.CompileContextImpl");
  private final Project myProject;
  private final CompilerProgressIndicator myProgressIndicator;
  private final Map<CompilerMessageCategory, Collection<CompilerMessage>> myMessages = new HashMap<CompilerMessageCategory, Collection<CompilerMessage>>();
  private CompileScope myCompileScope;
  private final DependencyCache myDependencyCache;
  private final CompileDriver myCompileDriver;
  private final boolean myMake;
  private boolean myRebuildRequested = false;
  private String myRebuildReason;
  private final Map<VirtualFile, Module> myRootToModuleMap = new HashMap<VirtualFile, Module>();
  private final Map<Module, Set<VirtualFile>> myModuleToRootsMap = new HashMap<Module, Set<VirtualFile>>();
  private final VirtualFile[] myOutputDirectories;

  public CompileContextImpl(Project project,
                            CompilerProgressIndicator indicator,
                            CompileScope compileScope,
                            DependencyCache dependencyCache,
                            CompileDriver compileDriver,
                            boolean isMake) {
    myProject = project;
    myProgressIndicator = indicator;
    myCompileScope = compileScope;
    myDependencyCache = dependencyCache;
    myCompileDriver = compileDriver;
    myMake = isMake;
    myOutputDirectories = CompilerPathsEx.getOutputDirectories(ModuleManager.getInstance(project).getModules());
  }

  public DependencyCache getDependencyCache() {
    return myDependencyCache;
  }

  public CompilerMessage[] getMessages(CompilerMessageCategory category) {
    Collection<CompilerMessage> collection = myMessages.get(category);
    if (collection == null) {
      return CompilerMessage.EMPTY_ARRAY;
    }
    return collection.toArray(new CompilerMessage[collection.size()]);
  }

  public void addMessage(CompilerMessageCategory category, String message, String url, int lineNum, int columnNum) {
    CompilerMessageImpl msg = new CompilerMessageImpl(myProject, category, message, url, lineNum, columnNum, null);
    addMessage(msg);
  }

  public void addMessage(CompilerMessageCategory category, String message, String url, int lineNum, int columnNum,
                         Navigatable navigatable) {
    CompilerMessageImpl msg = new CompilerMessageImpl(myProject, category, message, url, lineNum, columnNum, navigatable);
    addMessage(msg);
  }

  public void addMessage(CompilerMessage msg) {
    Collection<CompilerMessage> messages = myMessages.get(msg.getCategory());
    if (messages == null) {
      messages = new HashSet<CompilerMessage>();
      myMessages.put(msg.getCategory(), messages);
    }
    if (messages.add(msg)) {
      myProgressIndicator.addMessage(this, msg);
    }
  }

  public int getMessageCount(CompilerMessageCategory category) {
    if (category != null) {
      Collection<CompilerMessage> collection = myMessages.get(category);
      return collection != null ? collection.size() : 0;
    }
    int count = 0;
    for (Collection<CompilerMessage> collection : myMessages.values()) {
      if (collection != null) {
        count += collection.size();
      }
    }
    return count;
  }

  public CompileScope getCompileScope() {
    return myCompileScope;
  }

  public CompileScope getProjectCompileScope() {
    return myCompileDriver.myProjectCompileScope;
  }

  public void requestRebuildNextTime(String message) {
    if (!myRebuildRequested) {
      myRebuildRequested = true;
      myRebuildReason = message;
      addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
    }
  }

  public boolean isRebuildRequested() {
    return myRebuildRequested;
  }

  public String getRebuildReason() {
    return myRebuildReason;
  }

  public ProgressIndicator getProgressIndicator() {
    return myProgressIndicator;
  }

  public void assignModule(VirtualFile root, Module module) {
    try {
      myRootToModuleMap.put(root, module);
      Set<VirtualFile> set = myModuleToRootsMap.get(module);
      if (set == null) {
        set = new HashSet<VirtualFile>();
        myModuleToRootsMap.put(module, set);
      }
      set.add(root);
    }
    finally {
      myModuleToRootsCache.remove(module);
    }
  }

  public VirtualFile getSourceFileByOutputFile(VirtualFile outputFile) {
    if (myCompileDriver == null) {
      LOG.assertTrue(false, "myCompileDriver should not be null when calling getSourceFileByOutputFile()");
      return null;
    }
    TranslatingCompiler[] compilers = CompilerManager.getInstance(myProject).getCompilers(TranslatingCompiler.class);
    for (TranslatingCompiler compiler : compilers) {
      final TranslatingCompilerStateCache translatingCompilerCache = myCompileDriver.getTranslatingCompilerCache(compiler);
      if (translatingCompilerCache == null) {
        continue;
      }
      final String sourceUrl = translatingCompilerCache.getSourceUrl(outputFile.getPath());
      if (sourceUrl == null) {
        continue;
      }
      final VirtualFile sourceFile = VirtualFileManager.getInstance().findFileByUrl(sourceUrl);
      if (sourceFile != null) {
        return sourceFile;
      }
    }
    return null;
  }

  public Module getModuleByFile(VirtualFile file) {
    Module module = VfsUtil.getModuleForFile(myProject, file);
    if (module == null) {
      for (final VirtualFile root : myRootToModuleMap.keySet()) {
        if (VfsUtil.isAncestor(root, file, false)) {
          module = myRootToModuleMap.get(root);
          break;
        }
      }
    }
    return module;
  }


  private Map<Module, VirtualFile[]> myModuleToRootsCache = new HashMap<Module, VirtualFile[]>();

  public VirtualFile[] getSourceRoots(Module module) {
    VirtualFile[] cachedRoots = myModuleToRootsCache.get(module);
    if (cachedRoots != null) {
      if (areFilesValid(cachedRoots)) {
        return cachedRoots;
      }
      else {
        myModuleToRootsCache.remove(module); // clear cache for this module and rebuild list of roots
      }
    }

    Set<VirtualFile> additionalRoots = myModuleToRootsMap.get(module);
    VirtualFile[] moduleRoots = ModuleRootManager.getInstance(module).getSourceRoots();
    if (additionalRoots == null || additionalRoots.size() == 0) {
      myModuleToRootsCache.put(module, moduleRoots);
      return moduleRoots;
    }

    final VirtualFile[] allRoots = new VirtualFile[additionalRoots.size() + moduleRoots.length];
    System.arraycopy(moduleRoots, 0, allRoots, 0, moduleRoots.length);
    int index = moduleRoots.length;
    for (final VirtualFile additionalRoot : additionalRoots) {
      allRoots[index++] = additionalRoot;
    }
    myModuleToRootsCache.put(module, allRoots);
    return allRoots;
  }

  private boolean areFilesValid(VirtualFile[] files) {
    for (VirtualFile file : files) {
      if (!file.isValid()) {
        return false;
      }
    }
    return true;
  }

  public VirtualFile[] getAllOutputDirectories() {
    return myOutputDirectories;
  }

  public VirtualFile getModuleOutputDirectory(Module module) {
    return CompilerPaths.getModuleOutputDirectory(module, false);
  }

  public VirtualFile getModuleOutputDirectoryForTests(Module module) {
    return CompilerPaths.getModuleOutputDirectory(module, true);
  }

  public boolean isMake() {
    return myMake;
  }

  public void addScope(final CompileScope additionalScope) {
    myCompileScope = new CompositeScope(myCompileScope, additionalScope);
  }
}
