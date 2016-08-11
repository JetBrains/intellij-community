/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.fileTemplates.impl.FileTemplateBase;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.projectWizard.ProjectTemplateFileProcessor;
import com.intellij.ide.util.projectWizard.ProjectTemplateParameterFactory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformUtils;
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
  static final String FILE_HEADER_TEMPLATE_PLACEHOLDER = "<IntelliJ_File_Header>";

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    assert project != null;
    if (!ProjectUtil.isDirectoryBased(project)) {
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

      FileDocumentManager.getInstance().saveAllDocuments();

      ProgressManager.getInstance().run(new Task.Backgroundable(project, "Saving Project as Template", true, PerformInBackgroundOption.DEAF) {
        @Override
        public void run(@NotNull final ProgressIndicator indicator) {
          saveProject(project, file, moduleToSave, description, dialog.isReplaceParameters(), indicator, shouldEscape());
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
    VirtualFile baseDir = project.getBaseDir();
    return baseDir != null ? baseDir.findFileByRelativePath(path) : null;
  }

  public static void saveProject(final Project project,
                                 final File zipFile,
                                 Module moduleToSave,
                                 final String description,
                                 boolean replaceParameters,
                                 final ProgressIndicator indicator,
                                 boolean shouldEscape) {

    final Map<String, String> parameters = computeParameters(project, replaceParameters);
    indicator.setText("Saving project...");
    ApplicationManager.getApplication().invokeAndWait(() -> WriteAction.run(project::save),
                                                      indicator.getModalityState());
    indicator.setText("Processing project files...");
    ZipOutputStream stream = null;
    try {
      FileUtil.ensureExists(zipFile.getParentFile());
      stream = new ZipOutputStream(new FileOutputStream(zipFile));

      final VirtualFile dir = getDirectoryToSave(project, moduleToSave);
      writeFile(LocalArchivedTemplate.DESCRIPTION_PATH, description, project, dir, stream, true, indicator);
      if (replaceParameters) {
        String text = getInputFieldsText(parameters);
        writeFile(LocalArchivedTemplate.TEMPLATE_DESCRIPTOR, text, project, dir, stream, false, indicator);
      }

      String metaDescription = getTemplateMetaText(shouldEscape);
      writeFile(LocalArchivedTemplate.META_TEMPLATE_DESCRIPTOR_PATH, metaDescription, project, dir, stream, true, indicator);

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
                    !fileName.equals(LocalArchivedTemplate.TEMPLATE_META_XML) &&
                    !fileName.equals("misc.xml") &&
                    !fileName.equals("modules.xml") &&
                    !fileName.equals("workspace.xml") &&
                    !fileName.endsWith(".iml")) {
                  return true;
                }
              }

              ZipUtil.addFileToZip(finalStream, new File(virtualFile.getPath()), dir.getName() + "/" + relativePath, null, null, new ZipUtil.FileContentProcessor() {
                @Override
                public InputStream getContent(final File file) throws IOException {
                  if (virtualFile.getFileType().isBinary() || PROJECT_TEMPLATE_XML.equals(virtualFile.getName())) return STANDARD.getContent(file);
                  String result = getEncodedContent(virtualFile, project, parameters, getFileHeaderTemplateName(), shouldEscape);
                  return new ByteArrayInputStream(result.getBytes(CharsetToolkit.UTF8_CHARSET));
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
      UIUtil.invokeLaterIfNeeded(() -> Messages.showErrorDialog(project, "Can't save project as template", "Internal Error"));
    }
    finally {
      StreamUtil.closeStream(stream);
    }
  }

  static String getFileHeaderTemplateName() {
    if (PlatformUtils.isIntelliJ()) {
      return FileTemplateBase.getQualifiedName(FileTemplateManager.FILE_HEADER_TEMPLATE_NAME, "java");
    }
    else if (PlatformUtils.isPhpStorm()) {
      return FileTemplateBase.getQualifiedName("PHP File Header", "php");
    } else {
      throw new IllegalStateException("Provide file header template for your IDE");
    }
  }

  private static void writeFile(String path,
                                final String text,
                                Project project, VirtualFile dir, ZipOutputStream stream, boolean overwrite, ProgressIndicator indicator) throws IOException {
    final VirtualFile descriptionFile = getDescriptionFile(project, path);
    if (descriptionFile == null) {
      stream.putNextEntry(new ZipEntry(dir.getName() + "/" + path));
      stream.write(text.getBytes());
      stream.closeEntry();
    }
    else if (overwrite) {
      ApplicationManager.getApplication().invokeAndWait(() -> WriteAction.run(() -> {
        try {
          VfsUtil.saveText(descriptionFile, text);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }), indicator.getModalityState());
    }
  }

  public static Map<String, String> computeParameters(final Project project, boolean replaceParameters) {
    final Map<String, String> parameters = new HashMap<>();
    if (replaceParameters) {
      ApplicationManager.getApplication().runReadAction(() -> {
        ProjectTemplateParameterFactory[] extensions = Extensions.getExtensions(ProjectTemplateParameterFactory.EP_NAME);
        for (ProjectTemplateParameterFactory extension : extensions) {
          String value = extension.detectParameterValue(project);
          if (value != null) {
            parameters.put(value, extension.getParameterId());
          }
        }
      });
    }
    return parameters;
  }

  public static String getEncodedContent(VirtualFile virtualFile,
                                         Project project,
                                         Map<String, String> parameters) throws IOException {
    return getEncodedContent(virtualFile, project, parameters,
                             FileTemplateBase.getQualifiedName(FileTemplateManager.FILE_HEADER_TEMPLATE_NAME, "java"), true);
  }

  private static String getEncodedContent(VirtualFile virtualFile,
                                          Project project,
                                          Map<String, String> parameters,
                                          String fileHeaderTemplateName,
                                          boolean shouldEscape) throws IOException {
    String text = VfsUtilCore.loadText(virtualFile);
    final FileTemplate template = FileTemplateManager.getInstance(project).getDefaultTemplate(fileHeaderTemplateName);
    final String templateText = template.getText();
    final Pattern pattern = FileTemplateUtil.getTemplatePattern(template, project, new TIntObjectHashMap<>());
    String result = convertTemplates(text, pattern, templateText, shouldEscape);
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

  public static String convertTemplates(String input, Pattern pattern, String template, boolean shouldEscape) {
    Matcher matcher = pattern.matcher(input);
    int start = matcher.matches() ? matcher.start(1) : -1;
    if(!shouldEscape){
      if(start == -1){
        return input;
      } else {
        return input.substring(0, start) + FILE_HEADER_TEMPLATE_PLACEHOLDER + input.substring(matcher.end(1));
      }
    }
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
    Element element = new Element(ArchivedProjectTemplate.TEMPLATE);
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      Element field = new Element(ArchivedProjectTemplate.INPUT_FIELD);
      field.setText(entry.getValue());
      field.setAttribute(ArchivedProjectTemplate.INPUT_DEFAULT, entry.getKey());
      element.addContent(field);
    }
    return JDOMUtil.writeElement(element);
  }

  private static String getTemplateMetaText(boolean shouldEncode) {
    Element element = new Element(ArchivedProjectTemplate.TEMPLATE);
    element.setAttribute(LocalArchivedTemplate.UNENCODED_ATTRIBUTE, String.valueOf(!shouldEncode));
    return JDOMUtil.writeElement(element);
  }

  private static boolean shouldEscape() {
    return !PlatformUtils.isPhpStorm();
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = getEventProject(e);
    e.getPresentation().setEnabled(project != null && !project.isDefault());
  }
}
