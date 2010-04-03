/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author: Eugene Zhuravlev
 * Date: Jan 21, 2003
 * Time: 4:19:03 PM
 */
package com.intellij.compiler.impl;

import com.intellij.compiler.CompilerMessageImpl;
import com.intellij.compiler.make.DependencyCache;
import com.intellij.compiler.progress.CompilerTask;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.OrderedSet;
import com.intellij.util.indexing.FileBasedIndex;
import gnu.trove.TIntHashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CompileContextImpl extends UserDataHolderBase implements CompileContextEx {
  private final Project myProject;
  private final CompilerTask myTask;
  private final Map<CompilerMessageCategory, Collection<CompilerMessage>> myMessages = new EnumMap<CompilerMessageCategory, Collection<CompilerMessage>>(CompilerMessageCategory.class);
  private CompileScope myCompileScope;
  private final DependencyCache myDependencyCache;
  private final boolean myMake;
  private final boolean myIsRebuild;
  private boolean myRebuildRequested = false;
  private String myRebuildReason;
  private final Map<VirtualFile, Module> myRootToModuleMap = new HashMap<VirtualFile, Module>();
  private final Map<Module, Set<VirtualFile>> myModuleToRootsMap = new HashMap<Module, Set<VirtualFile>>();
  private final Set<VirtualFile> myGeneratedTestRoots = new java.util.HashSet<VirtualFile>();
  private VirtualFile[] myOutputDirectories;
  private Set<VirtualFile> myTestOutputDirectories;
  private final TIntHashSet myGeneratedSources = new TIntHashSet();
  private final ProjectFileIndex myProjectFileIndex; // cached for performance reasons
  private final ProjectCompileScope myProjectCompileScope;
  private final long myStartCompilationStamp;

  public CompileContextImpl(Project project,
                            CompilerTask compilerSession,
                            CompileScope compileScope,
                            DependencyCache dependencyCache, boolean isMake, boolean isRebuild) {
    myProject = project;
    myTask = compilerSession;
    myCompileScope = compileScope;
    myDependencyCache = dependencyCache;
    myMake = isMake;
    myIsRebuild = isRebuild;
    myStartCompilationStamp = System.currentTimeMillis();
    myProjectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    myProjectCompileScope = new ProjectCompileScope(myProject);

    if (compilerSession != null) {
      compilerSession.setContentIdKey(compileScope.getUserData(CompilerManager.CONTENT_ID_KEY));
    }
    recalculateOutputDirs();
  }

  public void recalculateOutputDirs() {
    final Module[] allModules = ModuleManager.getInstance(myProject).getModules();

    final Set<VirtualFile> allDirs = new OrderedSet<VirtualFile>((TObjectHashingStrategy<VirtualFile>)TObjectHashingStrategy.CANONICAL);
    final Set<VirtualFile> testOutputDirs = new java.util.HashSet<VirtualFile>();
    final Set<VirtualFile> productionOutputDirs = new java.util.HashSet<VirtualFile>();

    for (Module module : allModules) {
      final CompilerModuleExtension manager = CompilerModuleExtension.getInstance(module);
      final VirtualFile output = manager.getCompilerOutputPath();
      if (output != null && output.isValid()) {
        allDirs.add(output);
        productionOutputDirs.add(output);
      }
      final VirtualFile testsOutput = manager.getCompilerOutputPathForTests();
      if (testsOutput != null && testsOutput.isValid()) {
        allDirs.add(testsOutput);
        testOutputDirs.add(testsOutput);
      }
    }
    myOutputDirectories = VfsUtil.toVirtualFileArray(allDirs);
    // need this to ensure that the sent contains only _dedicated_ test output dirs
    // Directories that are configured for both test and production classes must not be added in the resulting set
    testOutputDirs.removeAll(productionOutputDirs);
    myTestOutputDirectories = Collections.unmodifiableSet(testOutputDirs);
  }

  public void markGenerated(Collection<VirtualFile> files) {
    for (final VirtualFile file : files) {
      myGeneratedSources.add(FileBasedIndex.getFileId(file));
    }
  }

  public long getStartCompilationStamp() {
    return myStartCompilationStamp;
  }

  public boolean isGenerated(VirtualFile file) {
    if (myGeneratedSources.contains(FileBasedIndex.getFileId(file))) {
      return true;
    }
    for (final VirtualFile root : myRootToModuleMap.keySet()) {
      if (VfsUtil.isAncestor(root, file, false)) {
        return true;
      }
    }
    final Module module = getModuleByFile(file);
    if (module != null) {
      final String procGenRoot = CompilerPaths.getAnnotationProcessorsGenerationPath(module);
      if (procGenRoot != null && VfsUtil.isAncestor(new File(procGenRoot), new File(file.getPath()), true)) {
        return true;
      }
    }
    return false;
  }

  public void updateZippedOuput(String outputDir, String relativePath) throws IOException {
    /*
    final File file = new File(outputDir, relativePath);
    final JBZipFile zip = lookupZip(outputDir);
    final long fileStamp = file.lastModified();
    if (fileStamp <= 0L) { // does not exist
      final JBZipEntry entry = zip.getEntry(relativePath);
      if (entry != null) {
        entry.erase();
      }
    }
    else {
      final JBZipEntry entry = zip.getOrCreateEntry(relativePath);
      if (entry.getTime() != fileStamp) {
        entry.setData(FileUtil.loadFileBytes(file), fileStamp);
      }
    }
    */
  }

  /*
  private JBZipFile lookupZip(String outputDir) {
    synchronized (myOpenZipFiles) {
      JBZipFile zip = myOpenZipFiles.get(outputDir);
      if (zip == null) {
        final File zipFile = CompilerPathsEx.getZippedOutputPath(myProject, outputDir);
        try {
          try {
            zip = new JBZipFile(zipFile);
          }
          catch (FileNotFoundException e) {
            try {
              zipFile.createNewFile();
              zip = new JBZipFile(zipFile);
            }
            catch (IOException e1) {
              zipFile.getParentFile().mkdirs();
              zipFile.createNewFile();
              zip = new JBZipFile(zipFile);
            }
          }
          myOpenZipFiles.put(outputDir, zip);
        }
        catch (IOException e) {
          LOG.info(e);
          addMessage(CompilerMessageCategory.ERROR, "Cannot create zip file " + zipFile.getPath() + ": " + e.getMessage(), null, -1, -1);
        }
      }
      return zip;
    }
  }
  */

  public void commitZipFiles() {
    /*
    synchronized (myOpenZipFiles) {
      for (JBZipFile zipFile : myOpenZipFiles.values()) {
        try {
          zipFile.close();
        }
        catch (IOException e) {
          LOG.info(e);
          addMessage(CompilerMessageCategory.ERROR, "Cannot save zip files: " + e.getMessage(), null, -1, -1);
        }
      }
      myOpenZipFiles.clear();
    }
    */
  }

  public void commitZip(String outputDir) throws IOException {
    /*
    synchronized (myOpenZipFiles) {
      JBZipFile zip = myOpenZipFiles.remove(outputDir);
      if (zip != null) {
        zip.close();
      }
    }
    */
  }

  public Project getProject() {
    return myProject;
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
      myTask.addMessage(msg);
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
    return myProjectCompileScope;
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
    return myTask.getIndicator();
  }

  public void assignModule(@NotNull VirtualFile root, @NotNull Module module, final boolean isTestSource) {
    try {
      myRootToModuleMap.put(root, module);
      Set<VirtualFile> set = myModuleToRootsMap.get(module);
      if (set == null) {
        set = new HashSet<VirtualFile>();
        myModuleToRootsMap.put(module, set);
      }
      set.add(root);
      if (isTestSource) {
        myGeneratedTestRoots.add(root);
      }
    }
    finally {
      myModuleToRootsCache.remove(module);
    }
  }

  @Nullable
  public VirtualFile getSourceFileByOutputFile(VirtualFile outputFile) {
    return TranslatingCompilerFilesMonitor.getSourceFileByOutput(outputFile);
  }

  public Module getModuleByFile(VirtualFile file) {
    final Module module = myProjectFileIndex.getModuleForFile(file);
    if (module != null) {
      return module;
    }
    for (final VirtualFile root : myRootToModuleMap.keySet()) {
      if (VfsUtil.isAncestor(root, file, false)) {
        return myRootToModuleMap.get(root);
      }
    }
    return null;
  }


  private final Map<Module, VirtualFile[]> myModuleToRootsCache = new HashMap<Module, VirtualFile[]>();

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
    if (additionalRoots == null || additionalRoots.isEmpty()) {
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

  private static boolean areFilesValid(VirtualFile[] files) {
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

  @NotNull
  public Set<VirtualFile> getTestOutputDirectories() {
    return myTestOutputDirectories;
  }

  public VirtualFile getModuleOutputDirectory(Module module) {
    // todo: caching?
    return CompilerPaths.getModuleOutputDirectory(module, false);
  }

  public VirtualFile getModuleOutputDirectoryForTests(Module module) {
    // todo: caching?
    return CompilerPaths.getModuleOutputDirectory(module, true);
  }

  public boolean isMake() {
    return myMake;
  }

  public boolean isRebuild() {
    return myIsRebuild;
  }

  public void addScope(final CompileScope additionalScope) {
    myCompileScope = new CompositeScope(myCompileScope, additionalScope);
  }

  public boolean isInTestSourceContent(@NotNull final VirtualFile fileOrDir) {
    if (myProjectFileIndex.isInTestSourceContent(fileOrDir)) {
      return true;
    }
    for (final VirtualFile root : myGeneratedTestRoots) {
      if (VfsUtil.isAncestor(root, fileOrDir, false)) {
        return true;
      }
    }
    return false;
  }

  public boolean isInSourceContent(@NotNull final VirtualFile fileOrDir) {
    if (myProjectFileIndex.isInSourceContent(fileOrDir)) {
      return true;
    }
    for (final VirtualFile root : myRootToModuleMap.keySet()) {
      if (VfsUtil.isAncestor(root, fileOrDir, false)) {
        return true;
      }
    }
    return false;
  }
}
