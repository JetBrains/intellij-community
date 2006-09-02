package com.intellij.compiler.impl.rmiCompiler;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.RmicSettings;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.javaCompiler.CompilerParsingThread;
import com.intellij.compiler.impl.javaCompiler.CompilerParsingThreadImpl;
import com.intellij.compiler.make.Cache;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.compiler.make.CacheUtils;
import com.intellij.compiler.make.DependencyCache;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.compiler.ex.CompilerPathsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 29, 2004
 */

public class RmicCompiler implements ClassPostProcessingCompiler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.rmiCompiler.RmicCompiler");
  private final Project myProject;
  //private static final FileFilter CLASSES_AND_DIRECTORIES_FILTER = new FileFilter() {
  //  public boolean accept(File pathname) {
  //    return pathname.isDirectory() || pathname.getName().endsWith(".class");
  //  }
  //};
  //private static final String REMOTE_INTERFACE_NAME = Remote.class.getName();

  public RmicCompiler(Project project) {
    myProject = project;
  }

  public @NotNull ProcessingItem[] getProcessingItems(final CompileContext context) {
    if (!RmicSettings.getInstance(myProject).IS_EANABLED) {
      return ProcessingItem.EMPTY_ARRAY;
    }
    final List<ProcessingItem> items = new ArrayList<ProcessingItem>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        DependencyCache dependencyCache = ((CompileContextEx)context).getDependencyCache();
        try {
          final Cache cache = dependencyCache.getCache();
          final int[] allClassNames = cache.getAllClassNames();
          final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
          final LocalFileSystem lfs = LocalFileSystem.getInstance();
          for (final int className : allClassNames) {
            final int classId = cache.getClassId(className);
            final boolean isRemoteObject = cache.isRemote(classId) && !CacheUtils.isInterface(cache, className);
            if (!isRemoteObject && !dependencyCache.wasRemote(className)) {
              continue;
            }
            final String outputPath = cache.getPath(classId);
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

            LOG.assertTrue(VfsUtil.isAncestor(outputDir, outputClassFile, true));

            final ProcessingItem item = createProcessingItem(module, outputClassFile, outputDir,
                                                             isRemoteObject, dependencyCache.resolve(className));
            items.add(item);
          }
        }
        catch (CacheCorruptedException e) {
          context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
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
    if (!RmicSettings.getInstance(myProject).IS_EANABLED) {
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

      final JavacOutputParserPool parserPool = new JavacOutputParserPool(myProject, context);

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
          if (dirItems.size() > 0) {
            final RmicProcessingItem[] successfullyProcessed = invokeRmic(context, parserPool, pair.getFirst(), dirItems, pair.getSecond());
            processed.addAll(Arrays.asList(successfullyProcessed));
          }
          progressIndicator.setFraction(((double)processed.size()) / ((double)items.length));
        }
        catch (IOException e) {
          context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
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

  private RmicProcessingItem[] invokeRmic(final CompileContext context,
                                          final JavacOutputParserPool parserPool, final Module module,
                                          final List<RmicProcessingItem> dirItems,
                                          final File outputDir
  ) throws IOException{

    final ProjectJdk jdk = ModuleRootManager.getInstance(module).getJdk();

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
      StringBuffer buf = new StringBuffer();
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
    final CompilerParsingThread parsingThread = new CompilerParsingThreadImpl(process, context, outputParser, false, true) {
      protected void processCompiledClass(String classFileToProcess) {
        final RmicProcessingItem item = pathToItemMap.get(classFileToProcess.replace(File.separatorChar, '/'));
        if (item != null) {
          successfullyCompiledItems.add(item);
        }
      }
    };
    parsingThread.start();
    try {
      parsingThread.join();
    }
    catch (InterruptedException e) {
    }
    return successfullyCompiledItems.toArray(new RmicProcessingItem[successfullyCompiledItems.size()]);
  }

  // todo: Module -> ModuleChunk
  private String[] createStartupCommand(final Module module, final String outputPath, final RmicProcessingItem[] items) {
    final ProjectJdk jdk = ModuleRootManager.getInstance(module).getJdk();

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

    commandLine.addAll(Arrays.asList(RmicSettings.getInstance(myProject).getOptions()));

    commandLine.add("-classpath");

    commandLine.add(CompilerPathsEx.getCompilationClasspath(module));

    commandLine.add("-d");

    commandLine.add(outputPath);

    for (RmicProcessingItem item : items) {
      commandLine.add(item.getClassQName());
    }
    return commandLine.toArray(new String[commandLine.size()]);
  }

  public @NotNull String getDescription() {
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


  public ValidityState createValidityState(DataInputStream is) throws IOException {
    return new RemoteClassValidityState(is.readLong(), is.readLong(), is.readLong(), is.readLong());
  }

  private static final class RemoteClassValidityState implements ValidityState {
    private long myRemoteClassTimestamp;
    private long myStubTimestamp;
    private long mySkelTimestamp;
    private long myTieTimestamp;

    public RemoteClassValidityState(long remoteClassTimestamp, long stubTimestamp, long skelTimestamp, long tieTimestamp) {
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

    public void save(DataOutputStream os) throws IOException {
      os.writeLong(myRemoteClassTimestamp);
      os.writeLong(myStubTimestamp);
      os.writeLong(mySkelTimestamp);
      os.writeLong(myTieTimestamp);
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

    public RmicProcessingItem(Module module, final VirtualFile outputClassFile, File outputDir, String qName) {
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
