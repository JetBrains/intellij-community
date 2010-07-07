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
package com.intellij.compiler.impl.rmiCompiler;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.javaCompiler.CompilerParsingThread;
import com.intellij.compiler.impl.javaCompiler.FileObject;
import com.intellij.compiler.make.Cache;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.compiler.make.DependencyCache;
import com.intellij.compiler.make.MakeUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 29, 2004
 */

public class RmicCompiler implements ClassPostProcessingCompiler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.rmiCompiler.RmicCompiler");
  //private static final FileFilter CLASSES_AND_DIRECTORIES_FILTER = new FileFilter() {
  //  public boolean accept(File pathname) {
  //    return pathname.isDirectory() || pathname.getName().endsWith(".class");
  //  }
  //};
  //private static final String REMOTE_INTERFACE_NAME = Remote.class.getName();

  @NotNull
  public ProcessingItem[] getProcessingItems(final CompileContext context) {
    if (!RmicConfiguration.getSettings(context.getProject()).IS_EANABLED) {
      return ProcessingItem.EMPTY_ARRAY;
    }
    final Project project = context.getProject();
    final List<ProcessingItem> items = new ArrayList<ProcessingItem>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        DependencyCache dependencyCache = ((CompileContextEx)context).getDependencyCache();
        try {
          final Cache cache = dependencyCache.getCache();
          final int[] allClassNames = cache.getAllClassNames();
          final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
          final LocalFileSystem lfs = LocalFileSystem.getInstance();
          for (final int className : allClassNames) {
            final boolean isRemoteObject = cache.isRemote(className) && !MakeUtil.isInterface(cache.getFlags(className));
            if (!isRemoteObject && !dependencyCache.wasRemote(className)) {
              continue;
            }
            final String outputPath = cache.getPath(className);
            if (outputPath == null) {
              continue;
            }
            final VirtualFile outputClassFile = lfs.findFileByPath(outputPath.replace(File.separatorChar, '/'));
            if (outputClassFile == null) {
              continue;
            }
            final VirtualFile sourceFile = ((CompileContextEx)context).getSourceFileByOutputFile(outputClassFile);
            if (sourceFile == null) {
              continue;
            }
            final Module module = context.getModuleByFile(sourceFile);
            if (module == null) {
              continue;
            }
            final VirtualFile outputDir = fileIndex.isInTestSourceContent(sourceFile)
                                          ? context.getModuleOutputDirectoryForTests(module)
                                          : context.getModuleOutputDirectory(module);
            if (outputDir == null) {
              continue;
            }

            if (!VfsUtil.isAncestor(outputDir, outputClassFile, true)) {
              LOG.error(outputClassFile.getPath() + " should be located under the output root " + outputDir.getPath());
            }

            final ProcessingItem item = createProcessingItem(module, outputClassFile, outputDir,
                                                             isRemoteObject, dependencyCache.resolve(className));
            items.add(item);
          }
        }
        catch (CacheCorruptedException e) {
          context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
          LOG.info(e);
        }
      }
    });

    return items.toArray(new ProcessingItem[items.size()]);
  }

  public static ProcessingItem createProcessingItem(final Module module,
                                             final VirtualFile outputClassFile,
                                             final VirtualFile outputDir,
                                             final boolean remoteObject, String qName) {
    final RmicProcessingItem item = new RmicProcessingItem(
      module, outputClassFile, new File(outputDir.getPath()), qName
    );
    item.setIsRemoteObject(remoteObject);
    return item;
  }

  public ProcessingItem[] process(CompileContext context, ProcessingItem[] items) {
    final Project project = context.getProject();
    if (!RmicConfiguration.getSettings(project).IS_EANABLED) {
      return ProcessingItem.EMPTY_ARRAY;
    }
    final ProgressIndicator progressIndicator = context.getProgressIndicator();
    progressIndicator.pushState();
    try {
      progressIndicator.setText(CompilerBundle.message("progress.generating.rmi.stubs"));
      final Map<Pair<Module, File>, List<RmicProcessingItem>> sortedByModuleAndOutputPath = new HashMap<Pair<Module,File>, List<RmicProcessingItem>>();
      for (ProcessingItem item1 : items) {
        final RmicProcessingItem item = (RmicProcessingItem)item1;
        final Pair<Module, File> moduleOutputPair = new Pair<Module, File>(item.getModule(), item.getOutputDir());
        List<RmicProcessingItem> dirItems = sortedByModuleAndOutputPath.get(moduleOutputPair);
        if (dirItems == null) {
          dirItems = new ArrayList<RmicProcessingItem>();
          sortedByModuleAndOutputPath.put(moduleOutputPair, dirItems);
        }
        dirItems.add(item);
      }
      final List<ProcessingItem> processed = new ArrayList<ProcessingItem>();

      final JavacOutputParserPool parserPool = new JavacOutputParserPool(project, context);

      for (final Pair<Module, File> pair : sortedByModuleAndOutputPath.keySet()) {
        if (progressIndicator.isCanceled()) {
          break;
        }
        final List<RmicProcessingItem> dirItems = sortedByModuleAndOutputPath.get(pair);
        try {
          // should delete all previously generated files for the remote class if there are any
          for (Iterator itemIterator = dirItems.iterator(); itemIterator.hasNext();) {
            final RmicProcessingItem item = (RmicProcessingItem)itemIterator.next();
            item.deleteGeneratedFiles();
            if (!item.isRemoteObject()) {
              itemIterator
                .remove(); // the object was remote and currently is not, so remove it from the list and do not generate stubs for it
            }
          }
          if (!dirItems.isEmpty()) {
            final RmicProcessingItem[] successfullyProcessed = invokeRmic(context, parserPool, pair.getFirst(), dirItems, pair.getSecond());
            ContainerUtil.addAll(processed, successfullyProcessed);
          }
          progressIndicator.setFraction(((double)processed.size()) / ((double)items.length));
        }
        catch (IOException e) {
          context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
          LOG.info(e);
        }
      }
      // update state so that the latest timestamps are recorded by make
      final ProcessingItem[] processedItems = processed.toArray(new ProcessingItem[processed.size()]);
      final List<File> filesToRefresh = new ArrayList<File>(processedItems.length * 3);
      for (ProcessingItem processedItem : processedItems) {
        RmicProcessingItem item = (RmicProcessingItem)processedItem;
        item.updateState();
        filesToRefresh.add(item.myStub);
        filesToRefresh.add(item.mySkel);
        filesToRefresh.add(item.myTie);
      }
      CompilerUtil.refreshIOFiles(filesToRefresh);
      return processedItems;
    }
    finally {
      progressIndicator.popState();
    }
  }

  private static RmicProcessingItem[] invokeRmic(final CompileContext context,
                                          final JavacOutputParserPool parserPool, final Module module,
                                          final List<RmicProcessingItem> dirItems,
                                          final File outputDir
  ) throws IOException{

    final Sdk jdk = ModuleRootManager.getInstance(module).getSdk();

    final Map<String, RmicProcessingItem> pathToItemMap = new HashMap<String, RmicProcessingItem>();
    final String[] cmdLine = ApplicationManager.getApplication().runReadAction(new Computable<String[]>() {
      public String[] compute() {
        for (final RmicProcessingItem item : dirItems) {
          pathToItemMap.put(item.myStub.getPath().replace(File.separatorChar, '/'), item);
          pathToItemMap.put(item.mySkel.getPath().replace(File.separatorChar, '/'), item);
          pathToItemMap.put(item.myTie.getPath().replace(File.separatorChar, '/'), item);
        }
        return createStartupCommand(module, outputDir.getPath(), dirItems.toArray(new RmicProcessingItem[dirItems.size()]));
      }
    });

    if (LOG.isDebugEnabled()) {
      StringBuilder buf = new StringBuilder();
      for (int idx = 0; idx < cmdLine.length; idx++) {
        if (idx > 0) {
          buf.append(" ");
        }
        buf.append(cmdLine[idx]);
      }
      LOG.debug(buf.toString());
    }

    // obtain parser before running the process because configuring parser may involve starting another process
    final OutputParser outputParser = parserPool.getJavacOutputParser(jdk);

    final Process process = Runtime.getRuntime().exec(cmdLine);
    final Set<RmicProcessingItem> successfullyCompiledItems = new HashSet<RmicProcessingItem>();
    final CompilerParsingThread parsingThread = new CompilerParsingThread(process, outputParser, false, true,context) {
      protected void processCompiledClass(FileObject classFileToProcess) {
        String key = classFileToProcess.getFile().getPath().replace(File.separatorChar, '/');
        final RmicProcessingItem item = pathToItemMap.get(key);
        if (item != null) {
          successfullyCompiledItems.add(item);
        }
      }
    };

    final Future<?> parsingThreadFuture = ApplicationManager.getApplication().executeOnPooledThread(parsingThread);
    try {
      process.waitFor();
    }
    catch (InterruptedException ignored) {
    }
    finally {
      parsingThread.setProcessTerminated(true);
    }

    try {
      parsingThreadFuture.get();
    }
    catch (InterruptedException ignored) {
    }
    catch (ExecutionException ignored) {
    }
    return successfullyCompiledItems.toArray(new RmicProcessingItem[successfullyCompiledItems.size()]);
  }

  // todo: Module -> ModuleChunk
  private static String[] createStartupCommand(final Module module, final String outputPath, final RmicProcessingItem[] items) {
    final Sdk jdk = ModuleRootManager.getInstance(module).getSdk();

    final VirtualFile homeDirectory = jdk.getHomeDirectory();
    if (homeDirectory == null) {
      throw new IllegalArgumentException(CompilerBundle.jdkHomeNotFoundMessage(jdk));
    }
    final String jdkPath = homeDirectory.getPath().replace('/', File.separatorChar);

    @NonNls final String compilerPath = jdkPath + File.separator + "bin" + File.separator + "rmic";

    @NonNls final List<String> commandLine = new ArrayList<String>();
    commandLine.add(compilerPath);

    CompilerUtil.addLocaleOptions(commandLine, true);

    commandLine.add("-verbose");

    ContainerUtil.addAll(commandLine, RmicConfiguration.getSettings(module.getProject()).getOptions());

    commandLine.add("-classpath");

    commandLine.add(getCompilationClasspath(module));

    commandLine.add("-d");

    commandLine.add(outputPath);

    for (RmicProcessingItem item : items) {
      commandLine.add(item.getClassQName());
    }
    return ArrayUtil.toStringArray(commandLine);
  }

  @NotNull
  public String getDescription() {
    return CompilerBundle.message("rmi.compiler.description");
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  /*
  private void addAllRemoteFilesFromModuleOutput(final CompileContext context, final Module module, final List<ProcessingItem> items, final File outputDir, File fromDir, final JavaClass remoteInterface) {
    final File[] children = fromDir.listFiles(CLASSES_AND_DIRECTORIES_FILTER);
    for (int idx = 0; idx < children.length; idx++) {
      final File child = children[idx];
      if (child.isDirectory()) {
        addAllRemoteFilesFromModuleOutput(context, module, items, outputDir, child, remoteInterface);
      }
      else {
        final String path = child.getPath();
        try {
          final ClassParser classParser = new ClassParser(path);
          final JavaClass javaClass = classParser.parse();
          // important! Need this in order to resolve other classes in the project (e.g. superclasses)
          javaClass.setRepository(BcelUtils.getActiveRepository());
          if (isRmicCompilable(javaClass, remoteInterface)) {
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                final VirtualFile outputClassFile = LocalFileSystem.getInstance().findFileByIoFile(child);
                if (outputClassFile != null) {
                  items.add(new RmicProcessingItem(module, outputClassFile, outputDir, javaClass.getClassName()));
                }
              }
            });
          }
        }
        catch (IOException e) {
          context.addMessage(CompilerMessageCategory.ERROR, "Cannot parse class file " + path + ": " + e.toString(), null, -1, -1);
        }
        catch (ClassFormatException e) {
          context.addMessage(CompilerMessageCategory.ERROR, "Class format exception: " + e.getMessage() + " File: " + path, null, -1, -1);
        }
      }
    }
  }
  */

  /*
  private boolean isRmicCompilable(final JavaClass javaClass, final JavaClass remoteInterface) {
    // stubs are needed for classes that _directly_ implement remote interfaces
    if (javaClass.isInterface() || isGenerated(javaClass)) {
      return false;
    }
    final JavaClass[] directlyImplementedInterfaces = javaClass.getInterfaces();
    if (directlyImplementedInterfaces != null) {
      for (int i = 0; i < directlyImplementedInterfaces.length; i++) {
        if (directlyImplementedInterfaces[i].instanceOf(remoteInterface)) {
          return true;
        }
      }
    }
    return false;
  }
  */

  /*
  private boolean isGenerated(JavaClass javaClass) {
    final String sourceFileName = javaClass.getSourceFileName();
    return sourceFileName == null || !sourceFileName.endsWith(".java");
  }
  */


  public ValidityState createValidityState(DataInput in) throws IOException {
    return new RemoteClassValidityState(in.readLong(), in.readLong(), in.readLong(), in.readLong());
  }

  private static String getCompilationClasspath(Module module) {
    final OrderEnumerator enumerator = ModuleRootManager.getInstance(module).orderEntries().withoutSdk().compileOnly().recursively().exportedOnly();
    final PathsList pathsList = enumerator.getPathsList();
    return pathsList.getPathsString();
  }

  private static final class RemoteClassValidityState implements ValidityState {
    private final long myRemoteClassTimestamp;
    private final long myStubTimestamp;
    private final long mySkelTimestamp;
    private final long myTieTimestamp;

    private RemoteClassValidityState(long remoteClassTimestamp, long stubTimestamp, long skelTimestamp, long tieTimestamp) {
      myRemoteClassTimestamp = remoteClassTimestamp;
      myStubTimestamp = stubTimestamp;
      mySkelTimestamp = skelTimestamp;
      myTieTimestamp = tieTimestamp;
    }

    public boolean equalsTo(ValidityState otherState) {
      if (otherState instanceof RemoteClassValidityState) {
        final RemoteClassValidityState state = (RemoteClassValidityState)otherState;
        return myRemoteClassTimestamp == state.myRemoteClassTimestamp &&
               myStubTimestamp == state.myStubTimestamp &&
               mySkelTimestamp == state.mySkelTimestamp &&
               myTieTimestamp == state.myTieTimestamp;
      }
      return false;
    }

    public void save(DataOutput out) throws IOException {
      out.writeLong(myRemoteClassTimestamp);
      out.writeLong(myStubTimestamp);
      out.writeLong(mySkelTimestamp);
      out.writeLong(myTieTimestamp);
    }
  }
  private static final class RmicProcessingItem implements ProcessingItem {
    private final Module myModule;
    private final VirtualFile myOutputClassFile;
    private final File myOutputDir;
    private final String myQName;
    private RemoteClassValidityState myState;
    private final File myStub;
    private final File mySkel;
    private final File myTie;
    private boolean myIsRemoteObject = false;

    private RmicProcessingItem(Module module, final VirtualFile outputClassFile, File outputDir, String qName) {
      myModule = module;
      myOutputClassFile = outputClassFile;
      myOutputDir = outputDir;
      myQName = qName;
      final String relativePath;
      final String baseName;

      final int index = qName.lastIndexOf('.');
      if (index >= 0) {
        relativePath = qName.substring(0, index + 1).replace('.', '/');
        baseName = qName.substring(index + 1);
      }
      else {
        relativePath = "";
        baseName = qName;
      }
      final String path = outputDir.getPath().replace(File.separatorChar, '/') + "/" + relativePath;
      //noinspection HardCodedStringLiteral
      myStub = new File(path + "/" + baseName + "_Stub.class");
      //noinspection HardCodedStringLiteral
      mySkel = new File(path + "/" + baseName + "_Skel.class");
      //noinspection HardCodedStringLiteral
      myTie = new File(path + "/_" + baseName + "_Tie.class");
      updateState();
    }

    public boolean isRemoteObject() {
      return myIsRemoteObject;
    }

    public void setIsRemoteObject(boolean isRemote) {
      myIsRemoteObject = isRemote;
    }

    @NotNull
    public VirtualFile getFile() {
      return myOutputClassFile;
    }

    public ValidityState getValidityState() {
      return myState;
    }

    public void updateState() {
      myState = new RemoteClassValidityState(
        myOutputClassFile.getTimeStamp(),
        getTimestamp(myStub),
        getTimestamp(mySkel),
        getTimestamp(myTie)
      );

    }

    private static long getTimestamp(File file) {
      long l = file.lastModified();
      return l == 0 ? -1L : l;
    }

    public void deleteGeneratedFiles() {
      if (FileUtil.delete(myStub)) {
        CompilerUtil.refreshIOFile(myStub);
      }
      if (FileUtil.delete(mySkel)) {
        CompilerUtil.refreshIOFile(mySkel);
      }
      if (FileUtil.delete(myTie)) {
        CompilerUtil.refreshIOFile(myTie);
      }
    }

    public String getClassQName() {
      return myQName;
    }

    public File getOutputDir() {
      return myOutputDir;
    }

    public Module getModule() {
      return myModule;
    }
  }
}
