package com.intellij.compiler.actions;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.ant.*;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class GenerateAntBuildAction extends CompileActionBase {
  @NonNls private static final String XML_EXTENSION = ".xml";

  protected void doAction(DataContext dataContext, final Project project) {
    CompilerConfiguration.getInstance(project).convertPatterns();
    final GenerateAntBuildDialog dialog = new GenerateAntBuildDialog(project);
    dialog.show();
    if (dialog.isOK()) {
      String[] names = dialog.getRepresentativeModuleNames();
      final GenerationOptions genOptions = new GenerationOptions(project, dialog.isGenerateSingleFileBuild(), dialog.isFormsCompilationEnabled(), dialog.isBackupFiles(), dialog.isForceTargetJdk(), names);
      generate(project, genOptions);
    }
  }

  public void update(AnActionEvent event){
    super.update(event);
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    presentation.setEnabled(project != null);
  }

  private void generate(final Project project, final GenerationOptions genOptions) {
    ApplicationManager.getApplication().saveAll();
    final List<File> filesToRefresh = new ArrayList<File>();
    try {
      final File[] generated;
      if (genOptions.generateSingleFile) {
        generated = generateSingleFileBuild(project, genOptions, filesToRefresh);
      }
      else {
        generated = generateMultipleFileBuild(project, genOptions, filesToRefresh);
      }
      if (generated != null) {
        StringBuffer filesString = new StringBuffer();
        for (int idx = 0; idx < generated.length; idx++) {
          final File file = generated[idx];
          if (idx > 0) {
            filesString.append(",\n");
          }
          filesString.append(file.getPath());
        }
        Messages.showInfoMessage(project, CompilerBundle.message("message.ant.files.generated.ok", filesString.toString()),
                                 CompilerBundle.message("generate.ant.build.title"));
      }
    }
    catch (IOException e) {
      Messages.showErrorDialog(project, CompilerBundle.message("error.ant.files.generate.failed", e.getMessage()),
                               CompilerBundle.message("generate.ant.build.title"));
    }
    finally {
      if (filesToRefresh.size() > 0) {
        CompilerUtil.refreshIOFiles(filesToRefresh);
      }
    }
  }

  private boolean backup(final File file, final Project project, GenerationOptions genOptions, List<File> filesToRefresh) {
    if (!genOptions.backupPreviouslyGeneratedFiles || !file.exists()) {
      return true;
    }
    final String path = file.getPath();
    final int extensionIndex = path.lastIndexOf(".");
    final String extension = path.substring(extensionIndex, path.length());
    //noinspection HardCodedStringLiteral
    final String backupPath = path.substring(0, extensionIndex) + "_" + new Date(file.lastModified()).toString().replaceAll("\\s+", "_").replaceAll(":", "-") + extension;
    final File backupFile = new File(backupPath);
    boolean ok;
    try {
      FileUtil.rename(file, backupFile);
      ok = true;
    }
    catch (IOException e) {
      Messages.showErrorDialog(project, CompilerBundle.message("error.ant.files.backup.failed", path),
                               CompilerBundle.message("generate.ant.build.title"));
      ok = false;
    }
    filesToRefresh.add(backupFile);
    return ok;
  }

  public File[] generateSingleFileBuild(Project project, GenerationOptions genOptions, List<File> filesToRefresh) throws IOException {
    final File projectBuildFileDestDir = VfsUtil.virtualToIoFile(project.getProjectFile().getParent());
    projectBuildFileDestDir.mkdirs();
    final File destFile = new File(projectBuildFileDestDir, BuildProperties.getProjectBuildFileName(project) + XML_EXTENSION);
    final File propertiesFile = new File(projectBuildFileDestDir, BuildProperties.getPropertyFileName(project));

    ensureFilesWritable(project, new File[] {destFile, propertiesFile});

    if (!backup(destFile, project, genOptions, filesToRefresh)) {
      return null;
    }
    if (!backup(propertiesFile, project, genOptions, filesToRefresh)) {
      return null;
    }

    generateSingleFileBuild(project, genOptions, destFile, propertiesFile);

    filesToRefresh.add(destFile);
    filesToRefresh.add(propertiesFile);
    return new File[] {destFile, propertiesFile};
  }

  public static void generateSingleFileBuild(final Project project,
                                             final GenerationOptions genOptions,
                                             final File buildxmlFile,
                                             final File propertiesFile) throws IOException {
    buildxmlFile.createNewFile();
    propertiesFile.createNewFile();
    final DataOutputStream dataOutput = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(buildxmlFile)));
    try {
      new SingleFileProjectBuild(project, genOptions).generate(dataOutput);
    }
    finally {
      dataOutput.close();
    }
    final DataOutputStream propertiesOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(propertiesFile)));
    try {
      new PropertyFileGenerator(project, genOptions).generate(propertiesOut);
    }
    finally {
      propertiesOut.close();
    }
  }

  private void ensureFilesWritable(Project project, File[] files) throws IOException {
    final List<VirtualFile> toCheck = new ArrayList<VirtualFile>(files.length);
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    for (File file : files) {
      final VirtualFile vFile = lfs.findFileByIoFile(file);
      if (vFile != null) {
        toCheck.add(vFile);
      }
    }
    final ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(toCheck.toArray(new VirtualFile[toCheck.size()]));
    if (status.hasReadonlyFiles()) {
      throw new IOException(status.getReadonlyFilesMessage());
    }
  }

  public File[] generateMultipleFileBuild(Project project, GenerationOptions genOptions, List<File> filesToRefresh) throws IOException {
    final File projectBuildFileDestDir = VfsUtil.virtualToIoFile(project.getProjectFile().getParent());
    projectBuildFileDestDir.mkdirs();
    final List<File> generated = new ArrayList<File>();
    final File projectBuildFile = new File(projectBuildFileDestDir, BuildProperties.getProjectBuildFileName(project) + XML_EXTENSION);
    final File propertiesFile = new File(projectBuildFileDestDir, BuildProperties.getPropertyFileName(project));
    final ModuleChunk[] chunks = genOptions.getModuleChunks();

    final File[] chunkFiles = new File[chunks.length];
    for (int idx = 0; idx < chunks.length; idx++) {
      final ModuleChunk chunk = chunks[idx];
      final File chunkBaseDir = BuildProperties.getModuleChunkBaseDir(chunk);
      chunkFiles[idx] = new File(chunkBaseDir, BuildProperties.getModuleChunkBuildFileName(chunk) + XML_EXTENSION);
    }

    final List<File> allFiles = new ArrayList<File>(2 + chunkFiles.length);
    allFiles.add(projectBuildFile);
    allFiles.add(propertiesFile);
    allFiles.addAll(Arrays.asList(chunkFiles));
    ensureFilesWritable(project, allFiles.toArray(new File[allFiles.size()]));

    if (!backup(projectBuildFile, project, genOptions, filesToRefresh)) {
      return null;
    }
    if (!backup(propertiesFile, project, genOptions, filesToRefresh)) {
      return null;
    }

    projectBuildFile.createNewFile();
    final DataOutputStream mainDataOutput = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(projectBuildFile)));
    try {

      final MultipleFileProjectBuild build = new MultipleFileProjectBuild(project, genOptions);
      build.generate(mainDataOutput);
      generated.add(projectBuildFile);

      // the sequence in which modules are imported is important cause output path properties for dependent modules should be defined first

      for (int idx = 0; idx < chunks.length; idx++) {
        final ModuleChunk chunk = chunks[idx];
        final File chunkBuildFile = chunkFiles[idx];
        final File chunkBaseDir = chunkBuildFile.getParentFile();
        if (chunkBaseDir != null) {
          chunkBaseDir.mkdirs();
        }
        final boolean moduleBackupOk = backup(chunkBuildFile, project, genOptions, filesToRefresh);
        if (!moduleBackupOk) {
          return null;
        }

        chunkBuildFile.createNewFile();
        final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(chunkBuildFile)));
        try {
          new ModuleChunkAntProject(project, chunk, genOptions).generate(out);
          generated.add(chunkBuildFile);
        }
        finally {
          out.close();
        }
      }
    }
    finally {
      mainDataOutput.close();
    }
    // properties
    final DataOutputStream propertiesOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(propertiesFile)));
    try {
      new PropertyFileGenerator(project, genOptions).generate(propertiesOut);
      generated.add(propertiesFile);
    }
    finally {
      propertiesOut.close();
    }

    filesToRefresh.addAll(generated);
    return generated.toArray(new File[generated.size()]);
  }

}