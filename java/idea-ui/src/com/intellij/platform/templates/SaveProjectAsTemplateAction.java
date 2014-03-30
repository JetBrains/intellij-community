/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.ide.util.projectWizard.ProjectTemplateFileProcessor;
import com.intellij.ide.util.projectWizard.ProjectTemplateParameterFactory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
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
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ZipUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntObjectHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
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
  private static final String PROJECT_TEMPLATE_XML = "project-template.xml";

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

    final VirtualFile descriptionFile = getDescriptionFile(project, LocalArchivedTemplate.DESCRIPTION_PATH);
    final SaveProjectAsTemplateDialog dialog = new SaveProjectAsTemplateDialog(project, descriptionFile);

    if (dialog.showAndGet()) {

      final Module moduleToSave = dialog.getModuleToSave();
      final File file = dialog.getTemplateFile();
      final String description = dialog.getDescription();

      ProgressManager.getInstance().run(new Task.Backgroundable(project, "Saving Project as Template", true, PerformInBackgroundOption.DEAF) {
        @Override
        public void run(@NotNull final ProgressIndicator indicator) {
          saveProject(project, file, moduleToSave, description, dialog.isReplaceParameters(), indicator);
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

  public static VirtualFile getDescriptionFile(Project project, String path) {
    return VfsUtil.findRelativeFile(path, project.getBaseDir());
  }

  public static void saveProject(final Project project,
                                 final File zipFile,
                                 Module moduleToSave,
                                 final String description,
                                 boolean replaceParameters,
                                 final ProgressIndicator indicator) {

    final Map<String, String> parameters = computeParameters(project, replaceParameters);
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
      writeFile(LocalArchivedTemplate.DESCRIPTION_PATH, description, project, dir, stream, true);
      if (replaceParameters) {
        String text = getInputFieldsText(parameters);
        writeFile(LocalArchivedTemplate.TEMPLATE_DESCRIPTOR, text, project, dir, stream, false);
      }

      FileIndex index = moduleToSave == null
                        ? ProjectRootManager.getInstance(project).getFileIndex()
                        : ModuleRootManager.getInstance(moduleToSave).getFileIndex();
      final ZipOutputStream finalStream = stream;

      index.iterateContent(new ContentIterator() {
        @Override
        public boolean processFile(final VirtualFile virtualFile) {
          if (!virtualFile.isDirectory()) {
            final String fileName = virtualFile.getName();
            indicator.setText2(fileName);
            try {
              String relativePath = VfsUtilCore.getRelativePath(virtualFile, dir, '/');
              if (relativePath == null) {
                throw new RuntimeException("Can't find relative path for " + virtualFile + " in " + dir);
              }
              final boolean system = Project.DIRECTORY_STORE_FOLDER.equals(virtualFile.getParent().getName());
              if (system) {
                if (!fileName.equals("description.html") &&
                    !fileName.equals(PROJECT_TEMPLATE_XML) &&
                    !fileName.equals("misc.xml") &&
                    !fileName.equals("modules.xml") &&
                    !fileName.equals("workspace.xml")) {
                  return true;
                }
              }

              ZipUtil.addFileToZip(finalStream, new File(virtualFile.getPath()), dir.getName() + "/" + relativePath, null, null, new ZipUtil.FileContentProcessor() {
                @Override
                public InputStream getContent(final File file) throws IOException {
                  if (virtualFile.getFileType().isBinary() || PROJECT_TEMPLATE_XML.equals(virtualFile.getName())) return STANDARD.getContent(file);
                  String result = ApplicationManager.getApplication().runReadAction(new ThrowableComputable<String, IOException>() {
                    @Override
                    public String compute() throws IOException {
                      return getEncodedContent(virtualFile, project, parameters);
                    }
                  });
                  return new ByteArrayInputStream(result.getBytes(TemplateModuleBuilder.UTF_8));
                }
              });
            }
            catch (IOException e) {
              LOG.error(e);
            }
          }
          indicator.checkCanceled();
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

  private static void writeFile(String path,
                                final String text,
                                Project project, VirtualFile dir, ZipOutputStream stream, boolean overwrite) throws IOException {
    final VirtualFile descriptionFile = getDescriptionFile(project, path);
    if (descriptionFile == null) {
      stream.putNextEntry(new ZipEntry(dir.getName() + "/" + path));
      stream.write(text.getBytes());
      stream.closeEntry();
    }
    else if (overwrite) {
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        public void run() {
          try {
            VfsUtil.saveText(descriptionFile, text);
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      });
    }
  }

  public static Map<String, String> computeParameters(final Project project, boolean replaceParameters) {
    final Map<String, String> parameters = new HashMap<String, String>();
    if (replaceParameters) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          ProjectTemplateParameterFactory[] extensions = Extensions.getExtensions(ProjectTemplateParameterFactory.EP_NAME);
          for (ProjectTemplateParameterFactory extension : extensions) {
            String value = extension.detectParameterValue(project);
            if (value != null) {
              parameters.put(value, extension.getParameterId());
            }
          }
        }
      });
    }
    return parameters;
  }

  public static String getEncodedContent(VirtualFile virtualFile,
                                          Project project,
                                          Map<String, String> parameters) throws IOException {
    final FileTemplate template = FileTemplateManager.getInstance().getDefaultTemplate(FileTemplateManager.FILE_HEADER_TEMPLATE_NAME);
    final String templateText = template.getText();
    final Pattern pattern = FileHeaderChecker.getTemplatePattern(template, project, new TIntObjectHashMap<String>());
    String text = VfsUtilCore.loadText(virtualFile);
    String result = convertTemplates(text, pattern, templateText);
    result = ProjectTemplateFileProcessor.encodeFile(result, virtualFile, project);
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      result = result.replace(entry.getKey(), "${" + entry.getValue() + "}");
    }
    return result;
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

  private static String getInputFieldsText(Map<String, String> parameters) {
    Element element = new Element(RemoteTemplatesFactory.TEMPLATE);
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      Element field = new Element(ArchivedProjectTemplate.INPUT_FIELD);
      field.setText(entry.getValue());
      field.setAttribute(RemoteTemplatesFactory.INPUT_DEFAULT, entry.getKey());
      element.addContent(field);
    }
    return JDOMUtil.writeElement(element);
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = getEventProject(e);
    e.getPresentation().setEnabled(project != null && !project.isDefault());
  }
}
