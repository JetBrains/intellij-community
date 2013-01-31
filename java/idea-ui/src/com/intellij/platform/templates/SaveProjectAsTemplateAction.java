/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.platform.templates;

import com.intellij.CommonBundle;
import com.intellij.codeInspection.defaultFileTemplateUsage.FileHeaderChecker;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ZipUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Dmitry Avdeev
 *         Date: 10/5/12
 */
public class SaveProjectAsTemplateAction extends AnAction {

  private static final Logger LOG = Logger.getInstance(SaveProjectAsTemplateAction.class);

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    assert project != null;
    StorageScheme scheme = ((ProjectEx)project).getStateStore().getStorageScheme();
    if (scheme != StorageScheme.DIRECTORY_BASED) {
      Messages.showErrorDialog(project, "Project templates do not support old .ipr (file-based) format.\n" +
                                        "Please convert your project via File->Save as Directory-Based format.", CommonBundle.getErrorTitle());
      return;
    }

    final VirtualFile descriptionFile = getDescriptionFile(project);
    final SaveProjectAsTemplateDialog dialog = new SaveProjectAsTemplateDialog(project, descriptionFile);

    if (dialog.showAndGet()) {

      final Module moduleToSave = dialog.getModuleToSave();
      final File file = dialog.getTemplateFile();
      final String description = dialog.getDescription();

      ProgressManager.getInstance().run(new Task.Backgroundable(project, "Saving Project as Template", true, PerformInBackgroundOption.DEAF) {
        @Override
        public void run(@NotNull final ProgressIndicator indicator) {
          saveProject(project, file, moduleToSave, description, indicator);
        }

        @Override
        public void onSuccess() {
          Messages.showInfoMessage(FileUtil.getNameWithoutExtension(file) + " was successfully created.\n" +
                                   "It's available now in Project Wizard", "Template Created");
        }

        @Override
        public void onCancel() {
          file.delete();
        }
      });
    }
  }

  public static VirtualFile getDescriptionFile(Project project) {
    return VfsUtil.findRelativeFile(LocalArchivedTemplate.DESCRIPTION_PATH, project.getBaseDir());
  }

  public static void saveProject(final Project project,
                                  final File zipFile,
                                  Module moduleToSave,
                                  final String description,
                                  final ProgressIndicator indicator) {

    indicator.setText("Saving project...");
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            project.save();
          }
        });
      }
    });
    indicator.setText("Processing project files...");
    ZipOutputStream stream = null;
    try {
      FileUtil.ensureExists(zipFile.getParentFile());
      stream = new ZipOutputStream(new FileOutputStream(zipFile));

      final VirtualFile dir = getDirectoryToSave(project, moduleToSave);
      final VirtualFile descriptionFile = getDescriptionFile(project);
      if (descriptionFile == null) {
        stream.putNextEntry(new ZipEntry(dir.getName() + "/" + LocalArchivedTemplate.DESCRIPTION_PATH));
        stream.write(description.getBytes());
        stream.closeEntry();
      }
      else {
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          public void run() {
            try {
              VfsUtil.saveText(descriptionFile, description);
            }
            catch (IOException e) {
              LOG.error(e);
            }
          }
        });
      }

      FileIndex index = moduleToSave == null
                        ? ProjectRootManager.getInstance(project).getFileIndex()
                        : ModuleRootManager.getInstance(moduleToSave).getFileIndex();
      final ZipOutputStream finalStream = stream;

      final FileTemplate template = FileTemplateManager.getInstance().getDefaultTemplate(FileTemplateManager.FILE_HEADER_TEMPLATE_NAME);
      final String templateText = template.getText();
      final Pattern pattern = FileHeaderChecker.getTemplatePattern(template, project, new TIntObjectHashMap<String>());

      index.iterateContent(new ContentIterator() {
        @Override
        public boolean processFile(final VirtualFile virtualFile) {
          if (!virtualFile.isDirectory()) {
            indicator.setText2(virtualFile.getName());
            try {
              String relativePath = VfsUtilCore.getRelativePath(virtualFile, dir, '/');
              if (relativePath == null) {
                throw new RuntimeException("Can't find relative path for " + virtualFile);
              }
              ZipUtil.addFileToZip(finalStream, new File(virtualFile.getPath()), dir.getName() + "/" + relativePath, null, null, new ZipUtil.FileContentProcessor() {
                @Override
                public InputStream getContent(File file) throws IOException {
                  if (virtualFile.getFileType().isBinary()) return STANDARD.getContent(file);

                  String s = VfsUtilCore.loadText(virtualFile);
                  String result = convertTemplates(s, pattern, templateText);

                  return new ByteArrayInputStream(result.getBytes(TemplateModuleBuilder.UTF_8));
                }
              });
            }
            catch (IOException e) {
              LOG.error(e);
            }
          }
          indicator.checkCanceled();
          // if (!".idea".equals(fileName.getParent())) return true;
          // todo filter out some garbage from .idea
          return true;
        }
      });
    }
    catch (Exception ex) {
      LOG.error(ex);
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        public void run() {
          Messages.showErrorDialog(project, "Can't save project as template", "Internal Error");
        }
      });
    }
    finally {
      StreamUtil.closeStream(stream);
    }
  }

  private static VirtualFile getDirectoryToSave(Project project, @Nullable Module module) {
    if (module == null) {
      return project.getBaseDir();
    }
    else {
      VirtualFile moduleFile = module.getModuleFile();
      assert moduleFile != null;
      return moduleFile.getParent();
    }
  }

  public static String convertTemplates(String input, Pattern pattern, String template) {
    Matcher matcher = pattern.matcher(input);
    int start = matcher.matches() ? matcher.start(1) : -1;
    StringBuilder builder = new StringBuilder(input.length() + 10);
    for (int i = 0; i < input.length(); i++) {
      if (start == i) {
        builder.append(template);
        //noinspection AssignmentToForLoopParameter
        i = matcher.end(1);
      }

      char c = input.charAt(i);
      if (c == '$' || c == '#') {
        builder.append('\\');
      }
      builder.append(c);
    }
    return builder.toString();
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = getEventProject(e);
    e.getPresentation().setEnabled(project != null && !project.isDefault());
  }
}
