/*
 * @author: Eugene Zhuravlev
 * Date: Jan 17, 2003
 * Time: 3:48:26 PM
 */
package com.intellij.compiler.impl.resourceCompiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.make.MakeUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ResourceCompiler implements TranslatingCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.resourceCompiler.ResourceCompiler");
  private final Project myProject;
  private final CompilerConfiguration myConfiguration;
  private static final FileTypeManager FILE_TYPE_MANAGER = FileTypeManager.getInstance();

  public ResourceCompiler(Project project, CompilerConfiguration compilerConfiguration) {
    myProject = project;
    myConfiguration = compilerConfiguration;
  }

  @NotNull
  public String getDescription() {
    return CompilerBundle.message("resource.compiler.description");
  }

  public boolean validateConfiguration(CompileScope scope) {
    ((CompilerConfigurationImpl) CompilerConfiguration.getInstance(myProject)).convertPatterns();
    return true;
  }

  public boolean isCompilableFile(VirtualFile file, CompileContext context) {
    return !StdFileTypes.JAVA.equals(FILE_TYPE_MANAGER.getFileTypeByFile(file)) && myConfiguration.isResourceFile(file);
  }

  public void compile(final CompileContext context, final VirtualFile[] files, OutputSink sink) {
    context.getProgressIndicator().pushState();
    context.getProgressIndicator().setText(CompilerBundle.message("progress.copying.resources"));

    final Map<String, Collection<OutputItem>> processed = new HashMap<String, Collection<OutputItem>>();
    final LinkedList<CopyCommand> copyCommands = new LinkedList<CopyCommand>();
    final long start = System.currentTimeMillis();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
        for (final VirtualFile file : files) {
          if (context.getProgressIndicator().isCanceled()) {
            break;
          }
          final Module module = context.getModuleByFile(file);
          if (module == null) {
            continue; // looks like file invalidated
          }
          final VirtualFile fileRoot = MakeUtil.getSourceRoot(context, module, file);
          if (fileRoot == null) {
            continue;
          }
          final String sourcePath = file.getPath();
          final String relativePath = VfsUtil.getRelativePath(file, fileRoot, '/');
          final String outputPath = CompilerPaths.getModuleOutputPath(module, ((CompileContextEx)context).isInTestSourceContent(file));
          if (outputPath == null) {
            continue;
          }
          final String packagePrefix = fileIndex.getPackageNameByDirectory(fileRoot);
          final String targetPath;
          if (packagePrefix != null && packagePrefix.length() > 0) {
            targetPath = outputPath+"/"+packagePrefix.replace('.', '/')+"/"+relativePath;
          }
          else {
            targetPath = outputPath+"/"+relativePath;
          }
          if (sourcePath.equals(targetPath)) {
            addToMap(processed, outputPath, new MyOutputItem(targetPath, file));
          }
          else {
            copyCommands.add(new CopyCommand(outputPath, sourcePath, targetPath, file));
          }
        }
      }
    });

    final Set<String> outputsToRefresh = new HashSet<String>();
    // do actual copy outside of read action to reduce the time the application is locked on it
    int idx = 0;
    final int total = copyCommands.size();
    CopyCommand.ourCopyingTime = 0L;
    while (!copyCommands.isEmpty()) {
      final CopyCommand command = copyCommands.removeFirst();
      if (context.getProgressIndicator().isCanceled()) {
        break;
      }
      context.getProgressIndicator().setFraction((idx++) * 1.0 / total);
      context.getProgressIndicator().setText2("Copying " + command.getFromPath() + "...");
      try {
        final MyOutputItem outputItem = command.copy(outputsToRefresh);
        addToMap(processed, command.getOutputPath(), outputItem);
      }
      catch (IOException e) {
        context.addMessage(
          CompilerMessageCategory.ERROR,
          CompilerBundle.message("error.copying", command.getFromPath(), command.getToPath(), e.getMessage()),
          command.getSourceFileUrl(), -1, -1
        );
      }
    }
    final long stop = System.currentTimeMillis();

    CompilerUtil.logDuration("Copying resources TOTAL", stop - start);
    CompilerUtil.logDuration("\tCopying resources (actual copying)", CopyCommand.ourCopyingTime);

    if (!outputsToRefresh.isEmpty()) {
      final List<File> dirs = new ArrayList<File>();
      for (String path : outputsToRefresh) {
        dirs.add(new File(path));
      }
      CompilerUtil.refreshIODirectories(dirs);
      outputsToRefresh.clear();
    }

    for (Iterator<Map.Entry<String, Collection<OutputItem>>> it = processed.entrySet().iterator(); it.hasNext();) {
      Map.Entry<String, Collection<OutputItem>> entry = it.next();
      sink.add(entry.getKey(), entry.getValue(), VirtualFile.EMPTY_ARRAY);
      it.remove(); // to free memory
    }
    context.getProgressIndicator().popState();
  }

  private static void addToMap(Map<String, Collection<OutputItem>> map, String outputDir, OutputItem item) {
    Collection<OutputItem> list = map.get(outputDir);
    if (list == null) {
      list = new ArrayList<OutputItem>();
      map.put(outputDir, list);
    }
    list.add(item);
  }

  private static class CopyCommand {
    private final String myOutputPath;
    private final String myFromPath;
    private final String myToPath;
    private final VirtualFile mySourceFile;
    public static long ourCopyingTime = 0L;

    private CopyCommand(String outputPath, String fromPath, String toPath, VirtualFile sourceFile) {
      myOutputPath = outputPath;
      myFromPath = fromPath;
      myToPath = toPath;
      mySourceFile = sourceFile;
    }

    public MyOutputItem copy(Collection<String> filesToRefresh) throws IOException {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Copying " + myFromPath + " to " + myToPath);
      }
      final File targetFile = new File(myToPath);
      final long start = System.currentTimeMillis();
      FileUtil.copyContent(new File(myFromPath), targetFile);
      ourCopyingTime += (System.currentTimeMillis() - start);
      filesToRefresh.add(myOutputPath);
      return new MyOutputItem(myToPath, mySourceFile);
    }

    public String getOutputPath() {
      return myOutputPath;
    }

    public String getFromPath() {
      return myFromPath;
    }

    public String getToPath() {
      return myToPath;
    }

    public String getSourceFileUrl() {
      // do not use mySourseFile.getUrl() directly as it requires read action
      return VirtualFileManager.constructUrl(mySourceFile.getFileSystem().getProtocol(), myFromPath);
    }
  }

  private static class MyOutputItem implements OutputItem {
    private final String myTargetPath;
    private final VirtualFile myFile;

    private MyOutputItem(String targetPath, VirtualFile sourceFile) {
      myTargetPath = targetPath;
      myFile = sourceFile;
    }

    public String getOutputPath() {
      return myTargetPath;
    }

    public VirtualFile getSourceFile() {
      return myFile;
    }
  }
}
