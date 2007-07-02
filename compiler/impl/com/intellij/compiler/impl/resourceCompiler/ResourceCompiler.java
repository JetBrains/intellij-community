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
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ResourceCompiler implements TranslatingCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.resourceCompiler.ResourceCompiler");
  private final Project myProject;
  private CompilerConfigurationImpl myConfiguration;
  private static final FileTypeManager FILE_TYPE_MANAGER = FileTypeManager.getInstance();

  public ResourceCompiler(Project project, CompilerConfigurationImpl compilerConfiguration) {
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
    return !FILE_TYPE_MANAGER.getFileTypeByFile(file).equals(StdFileTypes.JAVA) && myConfiguration.isResourceFile(file.getName());
  }

  public TranslatingCompiler.ExitStatus compile(final CompileContext context, final VirtualFile[] files) {
    context.getProgressIndicator().pushState();
    context.getProgressIndicator().setText(CompilerBundle.message("progress.copying.resources"));

    final List<OutputItem> processed = new ArrayList<OutputItem>(files.length);
    final List<CopyCommand> copyCommands = new ArrayList<CopyCommand>(files.length);
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
          final StringBuilder builder = StringBuilderSpinAllocator.alloc();
          try {
            if (packagePrefix != null && packagePrefix.length() > 0) {
              targetPath = builder.append(outputPath).append("/").append(packagePrefix.replace('.', '/')).append("/").append(relativePath).toString();
            }
            else {
              targetPath = builder.append(outputPath).append("/").append(relativePath).toString();
            }
          }
          finally {
            StringBuilderSpinAllocator.dispose(builder);
          }
          if (!sourcePath.equals(targetPath)) {
            copyCommands.add(new CopyCommand(outputPath, sourcePath, targetPath, file));
          }
          else {
            processed.add(new MyOutputItem(outputPath, targetPath, file));
          }
        }
      }
    });

    final List<File> filesToRefresh = new ArrayList<File>();
    // do actual copy outside of read action to reduce the time the application is locked on it
    for (int i = 0; i < copyCommands.size(); i++) {
      CopyCommand command = copyCommands.get(i);
      if (context.getProgressIndicator().isCanceled()) {
        break;
      }
      context.getProgressIndicator().setFraction(i * 1.0 / copyCommands.size());
      try {
        final MyOutputItem outputItem = command.copy(filesToRefresh);
        processed.add(outputItem);
      }
      catch (IOException e) {
        context.addMessage(
          CompilerMessageCategory.ERROR,
          CompilerBundle.message("error.copying", command.getFromPath(), command.getToPath(), e.getMessage()),
          command.getSourceFileUrl(), -1, -1
        );
      }
    }
    if (filesToRefresh.size() > 0) {
      CompilerUtil.refreshIOFiles(filesToRefresh);
    }

    context.getProgressIndicator().popState();

    final OutputItem[] itemsArray = processed.toArray(new OutputItem[processed.size()]);

    return new MyExitStatus(itemsArray);
  }

  private static class CopyCommand {
    private final String myOutputPath;
    private final String myFromPath;
    private final String myToPath;
    private final VirtualFile mySourceFile;

    public CopyCommand(String outputPath, String fromPath, String toPath, VirtualFile sourceFile) {
      myOutputPath = outputPath;
      myFromPath = fromPath;
      myToPath = toPath;
      mySourceFile = sourceFile;
    }

    public MyOutputItem copy(Collection<File> filesToRefresh) throws IOException {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Copying " + myFromPath + " to " + myToPath);
      }
      final File targetFile = new File(myToPath);
      FileUtil.copy(new File(myFromPath), targetFile);
      filesToRefresh.add(targetFile);
      return new MyOutputItem(myOutputPath, myToPath, mySourceFile);
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
    private final String myOutputPath;
    private final String myTargetPath;
    private final VirtualFile myFile;

    public MyOutputItem(String outputPath, String targetPath, VirtualFile file) {
      myOutputPath = outputPath;
      myTargetPath = targetPath;
      myFile = file;
    }

    public String getOutputRootDirectory() {
      return myOutputPath;
    }

    public String getOutputPath() {
      return myTargetPath;
    }
    public VirtualFile getSourceFile() {
      return myFile;
    }
  }

  private static class MyExitStatus implements ExitStatus {
    private final OutputItem[] myItemsArray;

    public MyExitStatus(OutputItem[] itemsArray) {
      myItemsArray = itemsArray;
    }

    public OutputItem[] getSuccessfullyCompiled() {
      return myItemsArray;
    }

    public VirtualFile[] getFilesToRecompile() {
      return VirtualFile.EMPTY_ARRAY;
    }
  }
}
