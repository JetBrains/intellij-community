package com.intellij.compiler.impl;

import com.intellij.ProjectTopics;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerIOUtil;
import com.intellij.compiler.make.MakeUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntLongHashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 3, 2008
 * 
 * A source file is scheduled for recompilation if
 * 1. its timestamp has changed
 * 2. one of its corresponding output files was deleted
 * 3. output root of containing module has changed
 * 
 * An output file is scheduled for deletion if:
 * 1. corresponding source file has been scheduled for recompilation (see above)
 * 2. corresponding source file has been deleted 
 */

public class TranslatingCompilerFilesMonitor implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.TranslatingCompilerFilesMonitor");
  @NonNls 
  private static final String PATHS_TO_DELETE_FILENAME = "paths_to_delete.dat";
  private static final FileAttribute ourSourceFileAttribute = new FileAttribute("_make_source_file_info_", 3);
  private static final FileAttribute ourOutputFileAttribute = new FileAttribute("_make_output_file_info_", 3);
  
  private final TIntObjectHashMap<TIntHashSet> mySourcesToRecompile = new TIntObjectHashMap<TIntHashSet>(); // ProjectId->set of source file paths
  private final TIntObjectHashMap<Map<String, SourceUrlClassNamePair>> myOutputsToDelete = new TIntObjectHashMap<Map<String, SourceUrlClassNamePair>>(); // Map: projectId -> Map{output path -> [sourceUrl; classname]}

  private final ProjectManager myProjectManager;

  public TranslatingCompilerFilesMonitor(VirtualFileManager vfsManager, ProjectManager projectManager) {
    myProjectManager = projectManager;

    projectManager.addProjectManagerListener(new MyProjectManagerListener());
    vfsManager.addVirtualFileListener(new MyVfsListener());
  }

  public static TranslatingCompilerFilesMonitor getInstance() {
    return ApplicationManager.getApplication().getComponent(TranslatingCompilerFilesMonitor.class);
  }

  @Nullable
  public static VirtualFile getSourceFileByOutput(VirtualFile outputFile) {
    final OutputFileInfo outputFileInfo = loadOutputInfo(outputFile);
    if (outputFileInfo != null) {
      final String path = outputFileInfo.getSourceFilePath();
      if (path != null) {
        return LocalFileSystem.getInstance().findFileByPath(path);
      }
    }
    return null;
  }

  public void collectFiles(CompileContext context, final TranslatingCompiler compiler, Iterator<VirtualFile> scopeSrcIterator, boolean forceCompile,
                           final boolean isRebuild,
                           Collection<VirtualFile> toCompile,
                           Collection<Trinity<File, String, Boolean>> toDelete) {
    final Project project = context.getProject();
    final int projectId = getProjectId(project);
    final CompilerConfiguration configuration = CompilerConfiguration.getInstance(project);
    final boolean _forceCompile = forceCompile || isRebuild;
    synchronized (mySourcesToRecompile) {
      final TIntHashSet pathsToRecompile = mySourcesToRecompile.get(projectId);
      if (_forceCompile || pathsToRecompile != null && !pathsToRecompile.isEmpty()) {
        while (scopeSrcIterator.hasNext()) {
          final VirtualFile file = scopeSrcIterator.next();
          if (configuration.isExcludedFromCompilation(file) || !compiler.isCompilableFile(file, context)) {
            continue;
          }
          final int fileId = getFileId(file);
          if (_forceCompile) {
            toCompile.add(file);
            if (pathsToRecompile == null || !pathsToRecompile.contains(fileId)) {
              addSourceForRecompilation(projectId, file, null);
            }
          }
          else if (pathsToRecompile.contains(fileId)) {
            toCompile.add(file);
          }
        }
      }
    }
    // it is important that files to delete are collected after the files to compile (see what happens if forceCompile == true)
    if (!isRebuild) {
      final CompileScope compileScope = context.getCompileScope();
      synchronized (myOutputsToDelete) {
        final Map<String, SourceUrlClassNamePair> outputsToDelete = myOutputsToDelete.get(projectId);
        if (outputsToDelete != null) {
          for (String outputPath : outputsToDelete.keySet()) {
            final SourceUrlClassNamePair classNamePair = outputsToDelete.get(outputPath);
            final String sourceUrl = classNamePair.getSourceUrl();
            final VirtualFile srcFile = VirtualFileManager.getInstance().findFileByUrl(sourceUrl);
            final boolean sourcePresent = srcFile != null;
            if (sourcePresent) {
              if (!compiler.isCompilableFile(srcFile, context)) {
                continue; // do not collect files that were compiled by another compiler
              }
              if (!compileScope.belongs(sourceUrl) && ((CompileContextEx)context).isInSourceContent(srcFile)) {
                continue;
              }
            }
            //noinspection UnnecessaryBoxing
            final File file = new File(outputPath);
            toDelete.add(new Trinity<File, String, Boolean>(file, classNamePair.getClassName(), Boolean.valueOf(sourcePresent)));
            if (LOG.isDebugEnabled()) {
              LOG.debug("Found file to delete: " + file);
            }
          }
        }
      }
    }
  }

  private static int getFileId(final VirtualFile file) {
    return FileBasedIndex.getFileId(file);
  }

  public void update(final CompileContext context, final TranslatingCompiler.OutputItem[] successfullyCompiled, final VirtualFile[] filesToRecompile)
      throws IOException {
    final Project project = context.getProject();
    final int projectId = getProjectId(project);

    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final Map<VirtualFile, SourceFileInfo> compiledSources = new HashMap<VirtualFile, SourceFileInfo>();
    for (TranslatingCompiler.OutputItem item : successfullyCompiled) {
      final VirtualFile sourceFile = item.getSourceFile();
      SourceFileInfo srcInfo = compiledSources.get(sourceFile);
      if (srcInfo == null) {
        srcInfo = loadSourceInfo(sourceFile);
        if (srcInfo != null) {
          srcInfo.clearPaths(projectId);
        }
        else {
          srcInfo = new SourceFileInfo();
        }
        compiledSources.put(sourceFile, srcInfo);
      }

      final String outputPath = item.getOutputPath();
      if (outputPath != null) { // can be null for packageinfo
        final String outputRoot = item.getOutputRootDirectory();
        final VirtualFile outputFile = lfs.findFileByPath(outputPath);
        assert outputFile != null : "Virtual file was not found for \"" + outputPath + "\"";
        if (!sourceFile.equals(outputFile)) {
          srcInfo.addOutputPath(projectId, outputPath);

          final String className = MakeUtil.relativeClassPathToQName(outputPath.substring(outputRoot.length()), '/');
          saveOutputInfo(outputFile, new OutputFileInfo(sourceFile.getPath(), className));
        }
      }
    }

    final long compilationStartStamp = ((CompileContextEx)context).getStartCompilationStamp();

    for (Map.Entry<VirtualFile, SourceFileInfo> entry : compiledSources.entrySet()) {
      final SourceFileInfo info = entry.getValue();
      final VirtualFile file = entry.getKey();
      final long fileStamp = file.getTimeStamp();
      info.updateTimestamp(projectId, fileStamp);
      saveSourceInfo(file, info);
      removeSourceForRecompilation(projectId, getFileId(file));
      if (fileStamp > compilationStartStamp) {
        // changes were made during compilation, need to re-schedule compilation
        // it is important to invoke removeSourceForRecompilation() before to make sure
        // the corresponding output paths will be scheduled for deletion
        addSourceForRecompilation(projectId, file, info);
      }
    }

    for (VirtualFile file : filesToRecompile) {
      addSourceForRecompilation(projectId, file, null);
    }
  }


  @NotNull
  public String getComponentName() {
    return "TranslatingCompilerFilesMonitor";
  }

  public void initComponent() {
    final File file = new File(CompilerPaths.getCompilerSystemDirectory(), PATHS_TO_DELETE_FILENAME);
    try {
      final DataInputStream is = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
      try {
        final int projectsCount = is.readInt();
        final LocalFileSystem lfs = LocalFileSystem.getInstance();
        synchronized (myOutputsToDelete) {
          for (int idx = 0; idx < projectsCount; idx++) {
            final int projectId = is.readInt();
            final int size = is.readInt();
            if (size > 0) {
              final Map<String, SourceUrlClassNamePair> map = new HashMap<String, SourceUrlClassNamePair>();
              myOutputsToDelete.put(projectId, map);
              for (int i = 0; i < size; i++) {
                final String outputPath = CompilerIOUtil.readString(is);
                final String srcUrl = CompilerIOUtil.readString(is);
                final String className = CompilerIOUtil.readString(is);
                if (lfs.findFileByPath(outputPath) != null) {
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("INIT path to delete: " + outputPath);
                  }
                  map.put(outputPath, new SourceUrlClassNamePair(srcUrl, className));
                }
              }
            }
          }
        }
      }
      finally {
        is.close();
      }
    }
    catch (FileNotFoundException ignored) {
    }
    catch (IOException e) {
      LOG.info(e);
      myOutputsToDelete.clear();
      file.delete();
    }
  }

  public void disposeComponent() {
    final File file = new File(CompilerPaths.getCompilerSystemDirectory(), PATHS_TO_DELETE_FILENAME);
    try {
      if (!file.exists()) {
        file.createNewFile();
      }
      final DataOutputStream os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
      try {
        synchronized (myOutputsToDelete) {
          final int[] keys = myOutputsToDelete.keys();
          os.writeInt(keys.length);
          for (int projectId : keys) {
            final Map<String, SourceUrlClassNamePair> projectOutputs = myOutputsToDelete.get(projectId);
            os.writeInt(projectId);
            if (projectOutputs != null) {
              os.writeInt(projectOutputs.size());
              for (Map.Entry<String, SourceUrlClassNamePair> entry : projectOutputs.entrySet()) {
                CompilerIOUtil.writeString(entry.getKey(), os);
                final SourceUrlClassNamePair pair = entry.getValue();
                CompilerIOUtil.writeString(pair.getSourceUrl(), os);
                CompilerIOUtil.writeString(pair.getClassName(), os);
              }
            }
            else {
              os.writeInt(0);
            }
          }
        }
      }
      finally {
        os.close();
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Nullable
  private static SourceFileInfo loadSourceInfo(final VirtualFile file) {
    final DataInputStream is = ourSourceFileAttribute.readAttribute(file);
    if (is != null) {
      try {
        try {
          return new SourceFileInfo(is);
        }
        finally {
          is.close();
        }
      }
      catch (IOException ignored) {
        LOG.info(ignored);
      }
    }
    return null;
  }

  private static void saveSourceInfo(VirtualFile file, SourceFileInfo descriptor) {
    final DataOutputStream out = ourSourceFileAttribute.writeAttribute(file);
    try {
      try {
        descriptor.save(out);
      }
      finally {
        out.close();
      }
    }
    catch (IOException ignored) {
      LOG.info(ignored);
    }
  }

  @Nullable
  private static OutputFileInfo loadOutputInfo(final VirtualFile file) {
    final DataInputStream is = ourOutputFileAttribute.readAttribute(file);
    if (is != null) {
      try {
        try {
          return new OutputFileInfo(is);
        }
        finally {
          is.close();
        }
      }
      catch (IOException ignored) {
        LOG.info(ignored);
      }
    }
    return null;
  }

  private static void saveOutputInfo(VirtualFile file, OutputFileInfo descriptor) {
    final DataOutputStream out = ourOutputFileAttribute.writeAttribute(file);
    try {
      try {
        descriptor.save(out);
      }
      finally {
        out.close();
      }
    }
    catch (IOException ignored) {
      LOG.info(ignored);
    }
  }

  private static int getProjectId(Project project) {
    try {
      return FSRecords.getNames().enumerate(project.getLocationHash());
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return -1;
  }

  private static class OutputFileInfo {
    private final int mySourcePath;

    private final int myClassName;

    OutputFileInfo(final String sourcePath, @Nullable String className) throws IOException {
      final PersistentStringEnumerator symtable = FSRecords.getNames();
      mySourcePath = symtable.enumerate(sourcePath);
      myClassName = className != null? symtable.enumerate(className) : -1;
    }

    OutputFileInfo(final DataInput in) throws IOException {
      mySourcePath = in.readInt();
      myClassName = in.readInt();
    }

    String getSourceFilePath() {
      try {
        return FSRecords.getNames().valueOf(mySourcePath);
      }
      catch (IOException e) {
        LOG.info(e);
      }
      return null;
    }

    @Nullable
    public String getClassName() {
      try {
        return myClassName < 0? null : FSRecords.getNames().valueOf(myClassName);
      }
      catch (IOException e) {
        LOG.info(e);
      }
      return null;
    }

    public void save(final DataOutput out) throws IOException {
      out.writeInt(mySourcePath);
      out.writeInt(myClassName);
    }
  }

  private static class SourceFileInfo {
    private TIntLongHashMap myTimestamps; // ProjectId -> last compiled stamp
    private TIntObjectHashMap<Serializable> myProjectToOutputPathMap; // ProjectId -> either a single output path or a set of output paths

    private SourceFileInfo() {
    }

    private SourceFileInfo(@NotNull DataInput in) throws IOException {
      final int projCount = in.readInt();
      for (int idx = 0; idx < projCount; idx++) {
        final int projectId = in.readInt();
        final long stamp = in.readLong();
        updateTimestamp(projectId, stamp);

        final int pathsCount = in.readInt();
        for (int i = 0; i < pathsCount; i++) {
          final int path = in.readInt();
          addOutputPath(projectId, path);
        }
      }
    }

    public void save(@NotNull final DataOutput out) throws IOException {
      final int[] projects = getProjectIds().toArray();
      out.writeInt(projects.length);
      for (int projectId : projects) {
        out.writeInt(projectId);
        out.writeLong(getTimestamp(projectId));
        final Object value = myProjectToOutputPathMap != null? myProjectToOutputPathMap.get(projectId) : null;
        if (value instanceof Integer) {
          out.writeInt(1);
          out.writeInt(((Integer)value).intValue());
        }
        else if (value instanceof TIntHashSet) {
          final TIntHashSet set = (TIntHashSet)value;
          out.writeInt(set.size());
          final IOException[] ex = new IOException[] {null};
          set.forEach(new TIntProcedure() {
            public boolean execute(final int value) {
              try {
                out.writeInt(value);
                return true;
              }
              catch (IOException e) {
                ex[0] = e;
                return false;
              }
            }
          });
          if (ex[0] != null) {
            throw ex[0];
          }
        }
        else {
          out.writeInt(0);
        }
      }
    }

    void updateTimestamp(final int projectId, final long stamp) {
      if (stamp > 0L) {
        if (myTimestamps == null) {
          myTimestamps = new TIntLongHashMap(1, 0.98f);
        }
        myTimestamps.put(projectId, stamp);
      }
      else {
        if (myTimestamps != null) {
          myTimestamps.remove(projectId);
        }
      }
    }

    TIntHashSet getProjectIds() {
      final TIntHashSet result = new TIntHashSet();
      if (myTimestamps != null) {
        result.addAll(myTimestamps.keys());
      }
      if (myProjectToOutputPathMap != null) {
        result.addAll(myProjectToOutputPathMap.keys());
      }
      return result;
    }

    private void addOutputPath(final int projectId, String outputPath) {
      try {
        addOutputPath(projectId, FSRecords.getNames().enumerate(outputPath));
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }

    private void addOutputPath(final int projectId, final int outputPath) {
      if (myProjectToOutputPathMap == null) {
        myProjectToOutputPathMap = new TIntObjectHashMap<Serializable>(1, 0.98f);
        myProjectToOutputPathMap.put(projectId, outputPath);
      }
      else {
        final Object val = myProjectToOutputPathMap.get(projectId);
        if (val == null)  {
          myProjectToOutputPathMap.put(projectId, outputPath);
        }
        else {
          TIntHashSet set;
          if (val instanceof Integer)  {
            set = new TIntHashSet();
            set.add(((Integer)val).intValue());
            myProjectToOutputPathMap.put(projectId, set);
          }
          else {
            assert val instanceof TIntHashSet;
            set = (TIntHashSet)val;
          }
          set.add(outputPath);
        }
      }
    }

    public void clearPaths(final int projectId){
      if (myProjectToOutputPathMap != null) {
        myProjectToOutputPathMap.remove(projectId);
      }
    }

    long getTimestamp(final int projectId) {
      return myTimestamps == null? -1L : myTimestamps.get(projectId);
    }

    void processOutputPaths(final int projectId, final Proc proc){
      if (myProjectToOutputPathMap != null) {
        try {
          final PersistentStringEnumerator symtable = FSRecords.getNames();
          final Object val = myProjectToOutputPathMap.get(projectId);
          if (val instanceof Integer)  {
            proc.execute(projectId, symtable.valueOf(((Integer)val).intValue()));
          }
          else if (val instanceof TIntHashSet) {
            ((TIntHashSet)val).forEach(new TIntProcedure() {
              public boolean execute(final int value) {
                try {
                  proc.execute(projectId, symtable.valueOf(value));
                  return true;
                }
                catch (IOException e) {
                  LOG.info(e);
                  return false;
                }
              }
            });
          }
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    }

    boolean isAssociated(int projectId, String outputPath) {
      if (myProjectToOutputPathMap != null) {
        try {
          final Object val = myProjectToOutputPathMap.get(projectId);
          if (val instanceof Integer)  {
            return FileUtil.pathsEqual(outputPath, FSRecords.getNames().valueOf(((Integer)val).intValue()));
          }
          if (val instanceof TIntHashSet) {
            final int _outputPath = FSRecords.getNames().enumerate(outputPath);
            return ((TIntHashSet)val).contains(_outputPath);
          }
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
      return false;
    }
  }


  private static interface FileProcessor {
    void execute(VirtualFile file);
  }

  private static void processRecursively(VirtualFile file, final FileProcessor processor) {
    if (file.getFileSystem() instanceof LocalFileSystem) {
      if (file.isDirectory()) {
        new Object() {
          void traverse(final VirtualFile dir) {
            for (VirtualFile child : dir.getChildren()) {
              if (child.isDirectory()) {
                traverse(child);
              }
              else {
                processor.execute(child);
              }
            }
          }
        }.traverse(file);
      }
      else {
        processor.execute(file);
      }
    }
  }

  // made public for tests
  public void scanSourceContent(final Project project, final Collection<VirtualFile> roots, final int totalRootCount, final boolean isNewRoots) {
    final int projectId = getProjectId(project);

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    int processed = 0;
    for (VirtualFile srcRoot : roots) {
      if (indicator != null) {
        indicator.setText2(srcRoot.getPresentableUrl());
        indicator.setFraction(++processed / (double)totalRootCount);
      }
      if (isNewRoots) {
        fileIndex.iterateContentUnderDirectory(srcRoot, new ContentIterator() {
          public boolean processFile(final VirtualFile file) {
            if (!file.isDirectory()) {
              if (!isMarkedForRecompilation(projectId, getFileId(file))) {
                final SourceFileInfo srcInfo = loadSourceInfo(file);
                if (srcInfo == null || srcInfo.getTimestamp(projectId) != file.getTimeStamp()) {
                  addSourceForRecompilation(projectId, file, srcInfo);
                }
              }
            }
            return true;
          }
        });
      }
      else {
        new Object() {
          void processFile(VirtualFile file) {
            if (file.isDirectory()) {
              for (VirtualFile child : file.getChildren()) {
                processFile(child);
              }
            }
            else {
              final int fileId = getFileId(file);
              if (fileId > 0 /*file is valid*/ && !isMarkedForRecompilation(projectId, fileId)) {
                final SourceFileInfo srcInfo = loadSourceInfo(file);
                if (srcInfo != null) {
                  addSourceForRecompilation(projectId, file, srcInfo);
                }
              }
            }
          }
        }.processFile(srcRoot);
      }
    }
  }


  private class MyProjectManagerListener extends ProjectManagerAdapter {

    final Map<Project, MessageBusConnection> myConnections = new HashMap<Project, MessageBusConnection>();

    public void projectOpened(final Project project) {
      final MessageBusConnection conn = project.getMessageBus().connect();
      myConnections.put(project, conn);
      conn.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
        private VirtualFile[] myRootsBefore;

        public void beforeRootsChange(final ModuleRootEvent event) {
          myRootsBefore = ProjectRootManager.getInstance(project).getContentSourceRoots();
        }

        public void rootsChanged(final ModuleRootEvent event) {
          final VirtualFile[] rootsAfter = ProjectRootManager.getInstance(project).getContentSourceRoots();

          final Set<VirtualFile> newRoots = new HashSet<VirtualFile>();
          newRoots.addAll(Arrays.asList(rootsAfter));
          if (myRootsBefore != null) {
            newRoots.removeAll(Arrays.asList(myRootsBefore));
          }
          scanSourceContent(project, newRoots, newRoots.size(), true);

          final Set<VirtualFile> oldRoots = new HashSet<VirtualFile>();
          if (myRootsBefore != null) {
            oldRoots.addAll(Arrays.asList(myRootsBefore));
          }
          if (!oldRoots.isEmpty()) {
            oldRoots.removeAll(Arrays.asList(rootsAfter));
          }
          scanSourceContent(project, oldRoots, oldRoots.size(), false);

          myRootsBefore = null;
        }
      });

      StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
        public void run() {
          new Task.Modal(project, CompilerBundle.message("compiler.initial.scanning.progress.text"), false) {
            public void run(@NotNull final ProgressIndicator indicator) {

              final IntermediateOutputCompiler[] compilers =
                  CompilerManager.getInstance(project).getCompilers(IntermediateOutputCompiler.class);

              final Set<VirtualFile> intermediateRoots = new HashSet<VirtualFile>();
              if (compilers.length > 0) {
                final Module[] modules = ModuleManager.getInstance(project).getModules();
                for (IntermediateOutputCompiler compiler : compilers) {
                  for (Module module : modules) {
                    final VirtualFile outputRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(CompilerPaths.getGenerationOutputPath(compiler, module, false));
                    if (outputRoot != null) {
                      intermediateRoots.add(outputRoot);
                    }
                    final VirtualFile testsOutputRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(CompilerPaths.getGenerationOutputPath(compiler, module, true));
                    if (testsOutputRoot != null) {
                      intermediateRoots.add(testsOutputRoot);
                    }
                  }
                }
              }

              final List<VirtualFile> projectRoots = Arrays.asList(ProjectRootManager.getInstance(project).getContentSourceRoots());
              final int totalRootsCount = projectRoots.size() + intermediateRoots.size();
              scanSourceContent(project, projectRoots, totalRootsCount, true);

              if (!intermediateRoots.isEmpty()) {
                final int projectId = getProjectId(project);
                final FileProcessor processor = new FileProcessor() {
                  public void execute(final VirtualFile file) {
                    if (!isMarkedForRecompilation(projectId, getFileId(file))) {
                      final SourceFileInfo srcInfo = loadSourceInfo(file);
                      if (srcInfo == null || srcInfo.getTimestamp(projectId) != file.getTimeStamp()) {
                        addSourceForRecompilation(projectId, file, srcInfo);
                      }
                    }
                  }
                };
                int processed = projectRoots.size();
                for (VirtualFile root : intermediateRoots) {
                  indicator.setText2(root.getPresentableUrl());
                  indicator.setFraction(++processed / (double)totalRootsCount);
                  processRecursively(root, processor);
                }
              }
            }
          }.queue();
        }
      });
    }

    public void projectClosed(final Project project) {
      myConnections.remove(project).disconnect();
      synchronized (mySourcesToRecompile) {
        mySourcesToRecompile.remove(getProjectId(project));
      }
    }
  }

  private class MyVfsListener extends VirtualFileAdapter {
    public void propertyChanged(final VirtualFilePropertyEvent event) {
      if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
        markDirtyIfSource(event.getFile());
      }
    }

    public void contentsChanged(final VirtualFileEvent event) {
      markDirtyIfSource(event.getFile());
    }

    public void fileCreated(final VirtualFileEvent event) {
      processNewFile(event.getFile());
    }

    public void fileCopied(final VirtualFileCopyEvent event) {
      processNewFile(event.getFile());
    }

    public void beforeFileDeletion(final VirtualFileEvent event) {
      final VirtualFile eventFile = event.getFile();
      processRecursively(eventFile, new FileProcessor() {
        public void execute(final VirtualFile file) {
          final String filePath = file.getPath();
          try {
            final OutputFileInfo outputInfo = loadOutputInfo(file);
            if (outputInfo != null) {
              final String srcPath = outputInfo.getSourceFilePath();
              final VirtualFile srcFile = srcPath != null? LocalFileSystem.getInstance().findFileByPath(srcPath) : null;
              if (srcFile != null) {
                final SourceFileInfo srcInfo = loadSourceInfo(srcFile);
                if (srcInfo != null) {
                  for (int projectId : srcInfo.getProjectIds().toArray()) {
                    if (srcInfo.isAssociated(projectId, filePath)) {
                      addSourceForRecompilation(projectId, srcFile, srcInfo);
                      break;
                    }
                  }
                }
              }
            }
  
            final SourceFileInfo srcInfo = loadSourceInfo(file);
            if (srcInfo != null) {
              final TIntHashSet projects = srcInfo.getProjectIds();
              if (!projects.isEmpty()) {
                final ScheduleOutputsForDeletionProc deletionProc = new ScheduleOutputsForDeletionProc(file.getUrl());
                for (int projectId : projects.toArray()) {
                  // mark associated outputs for deletion
                  srcInfo.processOutputPaths(projectId, deletionProc);
                  removeSourceForRecompilation(projectId, getFileId(file));
                }
              }
            }
          }
          finally {
            synchronized (myOutputsToDelete) {
              // it is important that update of myOutputsToDelete is done at the end
              // otherwise the filePath of the file that is about to be deleted may be re-scheduled for deletion in addSourceForRecompilation()
              for (int projectId : myOutputsToDelete.keys()) {
                final Map<String, SourceUrlClassNamePair> map = myOutputsToDelete.get(projectId);
                if (map != null) {
                  final SourceUrlClassNamePair val = map.remove(filePath);
                  if (val != null) {
                    if (LOG.isDebugEnabled()) {
                      LOG.debug("REMOVE path to delete: " + filePath);
                    }
                    if (map.isEmpty()) {
                      myOutputsToDelete.remove(projectId);
                    }
                  }
                }
              }
            }
          }
        }
      });
    }

    public void beforeFileMovement(final VirtualFileMoveEvent event) {
      markDirtyIfSource(event.getFile());
    }

    private void markDirtyIfSource(final VirtualFile file) {
      processRecursively(file, new FileProcessor() {
        public void execute(final VirtualFile file) {
          final SourceFileInfo srcInfo = file.isValid()? loadSourceInfo(file) : null;
          if (srcInfo != null) {
            for (int projectId : srcInfo.getProjectIds().toArray()) {
              addSourceForRecompilation(projectId, file, srcInfo);
            }
          }
        }
      });
    }

    private void processNewFile(VirtualFile file) {
      for (final Project project : myProjectManager.getOpenProjects()) {
        final ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
        if (rootManager.getFileIndex().isInSourceContent(file)) {
          final int projectId = getProjectId(project);
          final TranslatingCompiler[] translators = CompilerManager.getInstance(project).getCompilers(TranslatingCompiler.class);
          processRecursively(file, new FileProcessor() {
            public void execute(final VirtualFile file) {
              if (isCompilable(file)) {
                addSourceForRecompilation(projectId, file, null);
              }
            }

            boolean isCompilable(VirtualFile file) {
              for (TranslatingCompiler translator : translators) {
                if (translator.isCompilableFile(file, DummyCompileContext.getInstance())) {
                  return true;
                }
              }
              return false;
            }
          });
        }
        else {
          if (belongsToIntermediateSources(file, project)) {
            final int projectId = getProjectId(project);
            processRecursively(file, new FileProcessor() {
              public void execute(final VirtualFile file) {
                addSourceForRecompilation(projectId, file, null);
              }
            });
          }
        }
      }
    }
  }


  private static boolean belongsToIntermediateSources(VirtualFile file, Project project) {
    final IntermediateOutputCompiler[] srcGenerators = CompilerManager.getInstance(project).getCompilers(IntermediateOutputCompiler.class);
    if (srcGenerators.length == 0) {
      return false;
    }
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    if (modules.length == 0) {
      return false;
    }

    final String filePath = file.getPath();
    for (IntermediateOutputCompiler generator : srcGenerators) {
      for (Module module : modules) {
        String outputRoot = CompilerPaths.getGenerationOutputPath(generator, module, false);
        if (!outputRoot.endsWith("/")) {
          outputRoot += "/";
        }
        if (FileUtil.startsWith(filePath, outputRoot)) {
          return true;
        }

        String testsOutputRoot = CompilerPaths.getGenerationOutputPath(generator, module, true);
        if (!testsOutputRoot.endsWith("/")) {
          testsOutputRoot += "/";
        }
        if (FileUtil.startsWith(filePath, testsOutputRoot)) {
          return true;
        }
      }
    }

    return false;
  }

  private void addSourceForRecompilation(final int projectId, final VirtualFile srcFile, @Nullable final SourceFileInfo preloadedInfo) {
    final SourceFileInfo srcInfo = preloadedInfo != null? preloadedInfo : loadSourceInfo(srcFile);

    final boolean alreadyMarked;
    synchronized (mySourcesToRecompile) {
      TIntHashSet set = mySourcesToRecompile.get(projectId);
      if (set == null) {
        set = new TIntHashSet();
        mySourcesToRecompile.put(projectId, set);
      }
      alreadyMarked = !set.add(getFileId(srcFile));
    }

    if (!alreadyMarked && srcInfo != null) {
      srcInfo.updateTimestamp(projectId, -1L);
      srcInfo.processOutputPaths(projectId, new ScheduleOutputsForDeletionProc(srcFile.getUrl()));
      saveSourceInfo(srcFile, srcInfo);
    }
  }

  private void removeSourceForRecompilation(final int projectId, final int srcId) {
    synchronized (mySourcesToRecompile) {
      TIntHashSet set = mySourcesToRecompile.get(projectId);
      if (set != null) {
        set.remove(srcId);
        if (set.isEmpty()) {
          mySourcesToRecompile.remove(projectId);
        }
      }
    }
  }
  
  public boolean isMarkedForCompilation(Project project, VirtualFile file) {
    return isMarkedForRecompilation(getProjectId(project), getFileId(file));
  }
  
  private boolean isMarkedForRecompilation(int projectId, final int srcId) {
    synchronized (mySourcesToRecompile) {
      final TIntHashSet set = mySourcesToRecompile.get(projectId);
      return set != null && set.contains(srcId);
    }
  }
  
  private static interface Proc {
    boolean execute(final int projectId, String outputPath);
  }
  
  private class ScheduleOutputsForDeletionProc implements Proc {
    private final String mySrcUrl;
    private final LocalFileSystem myFileSystem;

    public ScheduleOutputsForDeletionProc(final String srcUrl) {
      mySrcUrl = srcUrl;
      myFileSystem = LocalFileSystem.getInstance();
    }

    public boolean execute(final int projectId, String outputPath) {
      final VirtualFile outFile = myFileSystem.findFileByPath(outputPath);
      if (outFile != null) { // not deleted yet
        final OutputFileInfo outputInfo = loadOutputInfo(outFile);
        final String classname = outputInfo != null? outputInfo.getClassName() : null;
        synchronized (myOutputsToDelete) {
          Map<String, SourceUrlClassNamePair> map = myOutputsToDelete.get(projectId);
          if (map == null) {
            map = new HashMap<String, SourceUrlClassNamePair>();
            myOutputsToDelete.put(projectId, map);
          }
          map.put(outputPath, new SourceUrlClassNamePair(mySrcUrl, classname));
          if (LOG.isDebugEnabled()) {
            LOG.debug("ADD path to delete: " + outputPath + "; source: " + mySrcUrl);
          }
        }
      }
      return true;
    }

  }
  
}
