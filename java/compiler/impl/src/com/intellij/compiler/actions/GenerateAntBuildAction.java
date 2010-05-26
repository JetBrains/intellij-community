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
package com.intellij.compiler.actions;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.ant.*;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

public class GenerateAntBuildAction extends CompileActionBase {
  @NonNls private static final String XML_EXTENSION = ".xml";

  protected void doAction(DataContext dataContext, final Project project) {
    ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(project)).convertPatterns();
    final GenerateAntBuildDialog dialog = new GenerateAntBuildDialog(project);
    dialog.show();
    if (dialog.isOK()) {
      final String[] names = dialog.getRepresentativeModuleNames();
      final GenerationOptionsImpl[] genOptions = {null};
      Runnable runnable = new Runnable() {
        public void run() {
          genOptions[0] = new GenerationOptionsImpl(project, dialog.isGenerateSingleFileBuild(), dialog.isFormsCompilationEnabled(),
                                                    dialog.isBackupFiles(), dialog.isForceTargetJdk(), dialog.isRuntimeClasspathInlined(),
                                                    dialog.isIdeaHomeGenerated(), names);
        }
      };
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, "Analyzing project structure...", true, project)) {
        return;
      }
      if (!validateGenOptions(project, genOptions[0])) {
        return;
      }
      generate(project, genOptions[0]);
    }
  }

  /**
   * Validate generation options and notify user about possible problems
   *
   * @param project    a context project
   * @param genOptions a generation optiosn
   * @return true if the generator should proceed with current options or if there is not conflict.
   */
  private static boolean validateGenOptions(Project project, GenerationOptionsImpl genOptions) {
    final Collection<String> EMPTY = Collections.emptyList();
    Collection<String> conflicts = EMPTY;
    for (ModuleChunk chunk : genOptions.getModuleChunks()) {
      final ChunkCustomCompilerExtension[] customeCompilers = chunk.getCustomCompilers();
      if (customeCompilers.length > 1) {
        if (conflicts == EMPTY) {
          conflicts = new LinkedList<String>();
        }
        conflicts.add(chunk.getName());
      }
    }
    if (!conflicts.isEmpty()) {
      StringBuilder msg = new StringBuilder();
      for (String conflictingChunk : conflicts) {
        msg.append(CompilerBundle.message("generate.ant.build.custom.compiler.conflict.message.row", conflictingChunk));
      }
      int rc = Messages
        .showOkCancelDialog(project, CompilerBundle.message("generate.ant.build.custom.compiler.conflict.message", msg.toString()),
                            CompilerBundle.message("generate.ant.build.custom.compiler.conflict.title"), Messages.getErrorIcon());
      if (rc != 0) {
        return false;
      }
    }
    return true;
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    presentation.setEnabled(project != null);
  }

  private void generate(final Project project, final GenerationOptions genOptions) {
    ApplicationManager.getApplication().saveAll();
    final List<File> filesToRefresh = new ArrayList<File>();
    final IOException[] _ex = new IOException[]{null};
    final List<File> _generated = new ArrayList<File>();

    try {
      if (genOptions.generateSingleFile) {
        final File projectBuildFileDestDir = VfsUtil.virtualToIoFile(project.getBaseDir());
        final File destFile = new File(projectBuildFileDestDir, BuildProperties.getProjectBuildFileName(project) + XML_EXTENSION);
        final File propertiesFile = new File(projectBuildFileDestDir, BuildProperties.getPropertyFileName(project));

        ensureFilesWritable(project, new File[]{destFile, propertiesFile});
      }
      else {
        final List<File> allFiles = new ArrayList<File>();

        final File projectBuildFileDestDir = VfsUtil.virtualToIoFile(project.getBaseDir());
        allFiles.add(new File(projectBuildFileDestDir, BuildProperties.getProjectBuildFileName(project) + XML_EXTENSION));
        allFiles.add(new File(projectBuildFileDestDir, BuildProperties.getPropertyFileName(project)));

        final ModuleChunk[] chunks = genOptions.getModuleChunks();
        for (final ModuleChunk chunk : chunks) {
          final File chunkBaseDir = BuildProperties.getModuleChunkBaseDir(chunk);
          allFiles.add(new File(chunkBaseDir, BuildProperties.getModuleChunkBuildFileName(chunk) + XML_EXTENSION));
        }

        ensureFilesWritable(project, allFiles.toArray(new File[allFiles.size()]));
      }

      new Task.Modal(project, CompilerBundle.message("generate.ant.build.title"), false) {
        public void run(@NotNull final ProgressIndicator indicator) {
          indicator.setIndeterminate(true);
          indicator.setText(CompilerBundle.message("generate.ant.build.progress.message"));
          try {
            final File[] generated;
            if (genOptions.generateSingleFile) {
              generated = generateSingleFileBuild(project, genOptions, filesToRefresh);
            }
            else {
              generated = generateMultipleFileBuild(project, genOptions, filesToRefresh);
            }
            if (generated != null) {
              _generated.addAll(Arrays.asList(generated));
            }
          }
          catch (IOException e) {
            _ex[0] = e;
          }
        }
      }.queue();

    }
    catch (IOException e) {
      _ex[0] = e;
    }

    if (_ex[0] != null) {
      Messages.showErrorDialog(project, CompilerBundle.message("error.ant.files.generate.failed", _ex[0].getMessage()),
                               CompilerBundle.message("generate.ant.build.title"));
    }
    else {
      StringBuffer filesString = new StringBuffer();
      for (int idx = 0; idx < _generated.size(); idx++) {
        final File file = _generated.get(idx);
        if (idx > 0) {
          filesString.append(",\n");
        }
        filesString.append(file.getPath());
      }
      Messages.showInfoMessage(project, CompilerBundle.message("message.ant.files.generated.ok", filesString.toString()),
                               CompilerBundle.message("generate.ant.build.title"));
    }

    if (filesToRefresh.size() > 0) {
      CompilerUtil.refreshIOFiles(filesToRefresh);
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
    final String backupPath = path.substring(0, extensionIndex) +
                              "_" +
                              new Date(file.lastModified()).toString().replaceAll("\\s+", "_").replaceAll(":", "-") +
                              extension;
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

  private File[] generateSingleFileBuild(Project project, GenerationOptions genOptions, List<File> filesToRefresh) throws IOException {
    final File projectBuildFileDestDir = VfsUtil.virtualToIoFile(project.getBaseDir());
    projectBuildFileDestDir.mkdirs();
    final File destFile = new File(projectBuildFileDestDir, BuildProperties.getProjectBuildFileName(project) + XML_EXTENSION);
    final File propertiesFile = new File(projectBuildFileDestDir, BuildProperties.getPropertyFileName(project));

    if (!backup(destFile, project, genOptions, filesToRefresh)) {
      return null;
    }
    if (!backup(propertiesFile, project, genOptions, filesToRefresh)) {
      return null;
    }

    generateSingleFileBuild(project, genOptions, destFile, propertiesFile);

    filesToRefresh.add(destFile);
    filesToRefresh.add(propertiesFile);
    return new File[]{destFile, propertiesFile};
  }

  public static void generateSingleFileBuild(final Project project,
                                             final GenerationOptions genOptions,
                                             final File buildxmlFile,
                                             final File propertiesFile) throws IOException {
    FileUtil.createIfDoesntExist(buildxmlFile);
    FileUtil.createIfDoesntExist(propertiesFile);
    final PrintWriter dataOutput = makeWriter(buildxmlFile);
    try {
      new SingleFileProjectBuild(project, genOptions).generate(dataOutput);
    }
    finally {
      dataOutput.close();
    }
    final PrintWriter propertiesOut = makeWriter(propertiesFile);
    try {
      new PropertyFileGeneratorImpl(project, genOptions).generate(propertiesOut);
    }
    finally {
      propertiesOut.close();
    }
  }

  /**
   * Create print writer over file with UTF-8 encoding
   *
   * @param buildxmlFile a file to write to
   * @return a created print writer
   * @throws UnsupportedEncodingException if endcoding not found
   * @throws FileNotFoundException        if file not found
   */
  private static PrintWriter makeWriter(final File buildxmlFile) throws UnsupportedEncodingException, FileNotFoundException {
    return new PrintWriter(new OutputStreamWriter(new FileOutputStream(buildxmlFile), "UTF-8"));
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
    final ReadonlyStatusHandler.OperationStatus status =
      ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(VfsUtil.toVirtualFileArray(toCheck));
    if (status.hasReadonlyFiles()) {
      throw new IOException(status.getReadonlyFilesMessage());
    }
  }

  public File[] generateMultipleFileBuild(Project project, GenerationOptions genOptions, List<File> filesToRefresh) throws IOException {
    final File projectBuildFileDestDir = VfsUtil.virtualToIoFile(project.getBaseDir());
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

    if (!backup(projectBuildFile, project, genOptions, filesToRefresh)) {
      return null;
    }
    if (!backup(propertiesFile, project, genOptions, filesToRefresh)) {
      return null;
    }

    FileUtil.createIfDoesntExist(projectBuildFile);
    final PrintWriter mainDataOutput = makeWriter(projectBuildFile);
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

        FileUtil.createIfDoesntExist(chunkBuildFile);
        final PrintWriter out = makeWriter(chunkBuildFile);
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
    final PrintWriter propertiesOut = makeWriter(propertiesFile);
    try {
      new PropertyFileGeneratorImpl(project, genOptions).generate(propertiesOut);
      generated.add(propertiesFile);
    }
    finally {
      propertiesOut.close();
    }

    filesToRefresh.addAll(generated);
    return generated.toArray(new File[generated.size()]);
  }

}
