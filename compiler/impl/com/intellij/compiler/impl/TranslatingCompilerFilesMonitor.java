package com.intellij.compiler.impl;

import com.intellij.ProjectTopics;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerIOUtil;
import com.intellij.compiler.make.MakeUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.TIntHashSet;
import gnu.trove.TObjectLongHashMap;
import gnu.trove.TObjectProcedure;
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
  private static final FileAttribute ourSourceFileAttribute = new FileAttribute("_make_source_file_info_", 1);
  private static final FileAttribute ourOutputFileAttribute = new FileAttribute("_make_output_file_info_", 1);
  
  private final Map<String, TIntHashSet> mySourcesToRecompile = new HashMap<String, TIntHashSet>(); // ProjectId->set of source file paths
  private final Map<String, SourceUrlClassNamePair> myOutputsToDelete = new HashMap<String, SourceUrlClassNamePair>(); // output path -> [sourceUrl; classname]
  
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
      return LocalFileSystem.getInstance().findFileByPath(path);
    }
    return null;
  }
  
  public void collectFiles(CompileContext context, final TranslatingCompiler compiler, Iterator<VirtualFile> scopeSrcIterator, 
                          boolean forceCompile,
                          Collection<VirtualFile> toCompile,
                          Collection<Trinity<File, String, Boolean>> toDelete) {
    final Project project = context.getProject();
    final String projectId = getProjectId(project);
    final CompilerConfiguration configuration = CompilerConfiguration.getInstance(project);
    synchronized (mySourcesToRecompile) {
      final TIntHashSet pathsToRecompile = mySourcesToRecompile.get(projectId);
      if (forceCompile || (pathsToRecompile != null && pathsToRecompile.size() > 0)) {
        while (scopeSrcIterator.hasNext()) {
          final VirtualFile file = scopeSrcIterator.next();
          if (configuration.isExcludedFromCompilation(file) || !compiler.isCompilableFile(file, context)) {
            continue;
          }
          final int fileId = getFileId(file);
          if (forceCompile) {
            toCompile.add(file);
            if (pathsToRecompile == null || !pathsToRecompile.contains(fileId)) {
              addSourceForRecompilation(Collections.singletonList(projectId), file, null);
            }
          }
          else if (pathsToRecompile.contains(fileId)) {
            toCompile.add(file);
          }
        }
      }
    }
    // it is important that files to delete are collected after the files to compile (see what happens if forceCompile == true)
    final CompileScope compileScope = context.getCompileScope();
    synchronized (myOutputsToDelete) {
      for (String outputPath : myOutputsToDelete.keySet()) {
        final SourceUrlClassNamePair classNamePair = myOutputsToDelete.get(outputPath);
        final String sourceUrl = classNamePair.getSourceUrl();
        if (compileScope.belongs(sourceUrl)) {
          final boolean sourcePresent = VirtualFileManager.getInstance().findFileByUrl(sourceUrl) != null;
          //noinspection UnnecessaryBoxing
          toDelete.add(new Trinity<File, String, Boolean>(new File(outputPath), classNamePair.getClassName(), Boolean.valueOf(sourcePresent)));
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
    final String projectId = getProjectId(project);

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

    for (Map.Entry<VirtualFile, SourceFileInfo> entry : compiledSources.entrySet()) {
      final SourceFileInfo info = entry.getValue();
      final VirtualFile file = entry.getKey();
      info.updateTimestamp(projectId, file.getTimeStamp());
      saveSourceInfo(file, info);
      removeSourceForRecompilation(projectId, getFileId(file));
    }

    final List<String> projects = Collections.singletonList(projectId);
    for (VirtualFile file : filesToRecompile) {
      addSourceForRecompilation(projects, file, null);
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
        final int size = is.readInt();
        final LocalFileSystem lfs = LocalFileSystem.getInstance();
        synchronized (myOutputsToDelete) {
          for (int idx = 0; idx < size; idx++) {
            final String outputPath = CompilerIOUtil.readString(is);
            final String srcUrl = CompilerIOUtil.readString(is);
            final String className = CompilerIOUtil.readString(is);
            if (lfs.findFileByPath(outputPath) != null) {
              myOutputsToDelete.put(outputPath, new SourceUrlClassNamePair(srcUrl, className));
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
      LOG.error(e);
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
          os.writeInt(myOutputsToDelete.size());
          for (Map.Entry<String, SourceUrlClassNamePair> entry : myOutputsToDelete.entrySet()) {
            CompilerIOUtil.writeString(entry.getKey(), os);
            final SourceUrlClassNamePair pair = entry.getValue();
            CompilerIOUtil.writeString(pair.getSourceUrl(), os);
            CompilerIOUtil.writeString(pair.getClassName(), os);
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
  
  private static String getProjectId(Project project) {
    return project.getLocationHash();
  }
  
  private static class OutputFileInfo {
    private final String mySourcePath;
    @Nullable
    private final String myClassName;

    OutputFileInfo(final String sourcePath, @Nullable String className) {
      mySourcePath = sourcePath;
      myClassName = className;
    }

    OutputFileInfo(final DataInput in) throws IOException {
      mySourcePath = CompilerIOUtil.readString(in);
      myClassName = CompilerIOUtil.readString(in);
    }

    String getSourceFilePath() {
      return mySourcePath;
    }

    @Nullable
    public String getClassName() {
      return myClassName;
    }

    public void save(final DataOutput out) throws IOException {
      CompilerIOUtil.writeString(mySourcePath, out);
      CompilerIOUtil.writeString(myClassName, out);
    }
  }

  private static class SourceFileInfo {
    private TObjectLongHashMap<String> myTimestamps; // ProjectId -> last compiled stamp
    private Map<String, Object> myProjectToOutputPathMap; // ProjectId -> either a single output path or a set of output paths 

    private SourceFileInfo() {
    }

    private SourceFileInfo(@NotNull DataInput in) throws IOException {
      final int projCount = in.readInt();
      for (int idx = 0; idx < projCount; idx++) {
        final String projectId = CompilerIOUtil.readString(in);
        final long stamp = in.readLong();
        updateTimestamp(projectId, stamp);

        final int pathsCount = in.readInt();
        for (int i = 0; i < pathsCount; i++) {
          final String path = CompilerIOUtil.readString(in);
          addOutputPath(projectId, path);
        }
      }
    }

    public void save(@NotNull final DataOutput out) throws IOException {
      final Collection<String> projects = getProjectIds();
      out.writeInt(projects.size());
      for (String projectId : projects) {
        CompilerIOUtil.writeString(projectId, out);
        out.writeLong(getTimestamp(projectId));
        final Object value = myProjectToOutputPathMap != null? myProjectToOutputPathMap.get(projectId) : null;
        if (value instanceof String) {
          out.writeInt(1);
          CompilerIOUtil.writeString(((String)value), out);
        }
        else if (value instanceof Set) {
          final Set<String> set = (Set<String>)value;
          out.writeInt(set.size());
          for (String path : set) {
            CompilerIOUtil.writeString(path, out);
          }
        }
        else {
          out.writeInt(0);
        }
      }
    }

    void updateTimestamp(final String projectId, final long stamp) {
      if (stamp > 0L) {
        if (myTimestamps == null) {
          myTimestamps = new TObjectLongHashMap<String>(1, 0.98f);
        }
        myTimestamps.put(projectId, stamp);
      }
      else {
        if (myTimestamps != null) {
          myTimestamps.remove(projectId);
        }
      }
    }

    Collection<String> getProjectIds() {
      if (myProjectToOutputPathMap == null && myTimestamps == null) {
        return Collections.emptyList(); 
      }
      if (myTimestamps == null) {
        return Collections.unmodifiableCollection(myProjectToOutputPathMap.keySet());
      }
      final int capacity = myTimestamps.size();
      final Set<String> result = new HashSet<String>(capacity);
      myTimestamps.forEachKey(new TObjectProcedure<String>() {
        public boolean execute(final String key) {
          result.add(key);
          return true;
        }
      });
      if (myProjectToOutputPathMap != null) {
        result.addAll(myProjectToOutputPathMap.keySet());
      }
      return Collections.unmodifiableCollection(result);
    }
    
    private void addOutputPath(final String projectId, String outputPath) {
      if (myProjectToOutputPathMap == null) {
        myProjectToOutputPathMap = new HashMap<String, Object>(1, 0.98f);
        myProjectToOutputPathMap.put(projectId, outputPath);
      }
      else {
        final Object val = myProjectToOutputPathMap.get(projectId);
        if (val == null)  {
          myProjectToOutputPathMap.put(projectId, outputPath);
        }
        else {
          Set<String> set;
          if (val instanceof String)  {
            set = new HashSet<String>();
            set.add(((String)val));
            myProjectToOutputPathMap.put(projectId, set);
          }
          else {
            assert val instanceof Set;
            set = (Set<String>)val;
          }
          set.add(outputPath);
        }
      }
    }

    public void clearPaths(final String projectId) {
      if (myProjectToOutputPathMap != null) {
        myProjectToOutputPathMap.remove(projectId);
      }
    }

    public long getLastCompilationTimestamp(final Project project) {
      final String projectId = getProjectId(project);
      return getTimestamp(projectId);
    }

    long getTimestamp(final String projectId) {
      return myTimestamps == null? -1L : myTimestamps.get(projectId);
    }

    void processOutputPaths(final String projectId, Proc proc) {
      if (myProjectToOutputPathMap != null) {
        final Object val = myProjectToOutputPathMap.get(projectId);
        if (val instanceof String)  {
          proc.execute(((String)val));
        }
        else if (val instanceof Set) {
          for (String path : (Set<String>)val) {
            proc.execute(path);
          }
        }
      }
    }
    
    boolean isAssociated(String projectId, String outputPath) {
      if (myProjectToOutputPathMap != null) {
        final Object val = myProjectToOutputPathMap.get(projectId);
        if (val instanceof String)  {
          return FileUtil.pathsEqual(outputPath, (String)val);
        }
        if (val instanceof Set) {
          return ((Set<String>)val).contains(outputPath);
        }
      }
      return false;
    }
  }

  
  private static interface FileProcessor {
    void execute(VirtualFile file);
  }
  
  private static void processRecursively(VirtualFile file, final FileProcessor processor) {
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

  // made public for tests
  public void scanSourceContent(final Project project, final Collection<VirtualFile> roots) {
    final String projectId = getProjectId(project);

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final List<String> projectIds = Collections.singletonList(projectId);
    for (VirtualFile srcRoot : roots) {
      fileIndex.iterateContentUnderDirectory(srcRoot, new ContentIterator() {
        public boolean processFile(final VirtualFile file) {
          if (!file.isDirectory()) {
            if (!isMarkedForRecompilation(projectId, getFileId(file))) {
              final SourceFileInfo srcInfo = loadSourceInfo(file);
              if (srcInfo == null || (srcInfo.getTimestamp(projectId) != file.getTimeStamp())) {
                addSourceForRecompilation(projectIds, file, srcInfo);
              }
            }
          }
          return true;
        }
      });
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
          Set<VirtualFile> roots = new HashSet<VirtualFile>();
          roots.addAll(Arrays.asList(rootsAfter));
          if (myRootsBefore != null) {
            roots.removeAll(Arrays.asList(myRootsBefore));
            myRootsBefore = null;
          }
          scanSourceContent(project, roots);
        }
      });

      StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
        public void run() {
          scanSourceContent(project, Arrays.asList(ProjectRootManager.getInstance(project).getContentSourceRoots()));
      
          final IntermediateOutputCompiler[] compilers =
              CompilerManager.getInstance(project).getCompilers(IntermediateOutputCompiler.class);

          if (compilers.length > 0) {
            final Set<VirtualFile> intermediateRoots = new HashSet<VirtualFile>();
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
        
            if (intermediateRoots.size() > 0) {
              final String projectId = getProjectId(project);
              final FileProcessor processor = new FileProcessor() {
                final List<String> projects = Collections.singletonList(projectId);
                public void execute(final VirtualFile file) {
                  if (!isMarkedForRecompilation(projectId, getFileId(file))) {
                    final SourceFileInfo srcInfo = loadSourceInfo(file);
                    if (srcInfo == null || (srcInfo.getTimestamp(projectId) != file.getTimeStamp())) {
                      addSourceForRecompilation(projects, file, srcInfo);
                    }
                  }
                }
              };
              for (VirtualFile root : intermediateRoots) {
                processRecursively(root, processor);
              }
            }
          }
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
      processRecursively(event.getFile(), new FileProcessor() {
        public void execute(final VirtualFile file) {
          final String filePath = file.getPath();
            
          final OutputFileInfo outputInfo = loadOutputInfo(file);
          if (outputInfo != null) {
            final String srcPath = outputInfo.getSourceFilePath();
            final VirtualFile srcFile = LocalFileSystem.getInstance().findFileByPath(srcPath);
            if (srcFile != null) {
              final SourceFileInfo srcInfo = loadSourceInfo(srcFile);
              if (srcInfo != null) {
                for (String projectId : srcInfo.getProjectIds()) {
                  if (srcInfo.isAssociated(projectId, filePath)) {
                    addSourceForRecompilation(Collections.singletonList(projectId), srcFile, srcInfo);
                    break;
                  }
                }
              }
            }
            synchronized (myOutputsToDelete) {
              myOutputsToDelete.remove(filePath);
            }
          }
            
          final SourceFileInfo srcInfo = loadSourceInfo(file); 
          if (srcInfo != null) {
            final Collection<String> projects = srcInfo.getProjectIds();
            if (projects.size() > 0) {
              final ScheduleOutputsForDeletionProc deletionProc = new ScheduleOutputsForDeletionProc(file.getUrl());
              for (String projectId : projects) {
                // mark associated outputs for deletion
                srcInfo.processOutputPaths(projectId, deletionProc);
                removeSourceForRecompilation(projectId, getFileId(file));
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
          final SourceFileInfo srcInfo = loadSourceInfo(file);
          if (srcInfo != null) {
            addSourceForRecompilation(srcInfo.getProjectIds(), file, srcInfo);
          }
        }
      });
    }
    
    private void processNewFile(VirtualFile file) {
      for (final Project project : myProjectManager.getOpenProjects()) {
        final ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
        if (rootManager.getFileIndex().isInSourceContent(file)) {
          final String projectId = getProjectId(project);
          final TranslatingCompiler[] translators = CompilerManager.getInstance(project).getCompilers(TranslatingCompiler.class);
          processRecursively(file, new FileProcessor() {
            public void execute(final VirtualFile file) {
              if (isCompilable(file)) {
                addSourceForRecompilation(Collections.singletonList(projectId), file, null);
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
            processRecursively(file, new FileProcessor() {
              public void execute(final VirtualFile file) {
                addSourceForRecompilation(Collections.singletonList(getProjectId(project)), file, null);
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
  
  private void addSourceForRecompilation(final Collection<String> projects, final VirtualFile srcFile, final @Nullable SourceFileInfo preloadedInfo) {
    final int srcFileId = getFileId(srcFile);
    final SourceFileInfo srcInfo = preloadedInfo != null? preloadedInfo : loadSourceInfo(srcFile);
    if (projects.size() > 0) {
      ScheduleOutputsForDeletionProc deletionProc = null;
      boolean isDirty = false;
      for (String projectId : projects) {
        final boolean alreadyMarked; 
        synchronized (mySourcesToRecompile) {
          TIntHashSet set = mySourcesToRecompile.get(projectId);
          if (set == null) {
            set = new TIntHashSet();
            mySourcesToRecompile.put(projectId, set);
          }
          alreadyMarked = !set.add(srcFileId);
        }

        if (!alreadyMarked && srcInfo != null) {
          isDirty = true;
          srcInfo.updateTimestamp(projectId, -1L);
          srcInfo.processOutputPaths(projectId, deletionProc != null? deletionProc : (deletionProc = new ScheduleOutputsForDeletionProc(srcFile.getUrl())));
        }
      }

      if (isDirty) {
        saveSourceInfo(srcFile, srcInfo);
      }
    }
  }                             
  
  private void removeSourceForRecompilation(final String projectId, final int srcId) {
    synchronized (mySourcesToRecompile) {
      TIntHashSet set = mySourcesToRecompile.get(projectId);
      if (set != null) {
        set.remove(srcId);
        if (set.size() == 0) {
          mySourcesToRecompile.remove(projectId);
        }
      }
    }
  }
  
  public boolean isMarkedForCompilation(Project project, VirtualFile file) {
    return isMarkedForRecompilation(getProjectId(project), getFileId(file));
  }
  
  private boolean isMarkedForRecompilation(String projectId, final int srcId) {
    synchronized (mySourcesToRecompile) {
      final TIntHashSet set = mySourcesToRecompile.get(projectId);
      return set != null && set.contains(srcId);
    }
  }
  
  private static interface Proc {
    boolean execute(String outputPath);
  }
  
  private class ScheduleOutputsForDeletionProc implements Proc {
    private final String mySrcUrl;
    private LocalFileSystem myFileSystem;

    public ScheduleOutputsForDeletionProc(final String srcUrl) {
      mySrcUrl = srcUrl;
      myFileSystem = LocalFileSystem.getInstance();
    }

    public boolean execute(String outputPath) {
      final VirtualFile outFile = myFileSystem.findFileByPath(outputPath);
      String classname = null;
      final OutputFileInfo outputInfo = outFile != null? loadOutputInfo(outFile) : null;
      if (outputInfo != null) {
        classname = outputInfo.getClassName();
      }
      synchronized (myOutputsToDelete) {
        myOutputsToDelete.put(outputPath, new SourceUrlClassNamePair(mySrcUrl, classname));
      }
      return true;
    }

  }
  
}
