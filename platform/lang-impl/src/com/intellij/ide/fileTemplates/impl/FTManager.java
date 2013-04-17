/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.fileTemplates.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 3/22/11
 */
class FTManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.impl.FTManager");
  public static final String TEMPLATES_DIR = "fileTemplates";
  public static final String DEFAULT_TEMPLATE_EXTENSION = "ft";
  public static final String TEMPLATE_EXTENSION_SUFFIX = "." + DEFAULT_TEMPLATE_EXTENSION;
  public static final String CONTENT_ENCODING = CharsetToolkit.UTF8;

  private final String myName;
  private final boolean myInternal;
  private final String myTemplatesDir;
  private final Map<String, FileTemplateBase> myTemplates = new HashMap<String, FileTemplateBase>();
  private volatile List<FileTemplateBase> mySortedTemplates;
  private final List<DefaultTemplate> myDefaultTemplates = new ArrayList<DefaultTemplate>();

  FTManager(@NotNull @NonNls String name, @NotNull @NonNls String defaultTemplatesDirName) {
    this(name, defaultTemplatesDirName, false);
  }

  FTManager(@NotNull @NonNls String name, @NotNull @NonNls String defaultTemplatesDirName, boolean internal) {
    myName = name;
    myInternal = internal;
    myTemplatesDir = TEMPLATES_DIR + (defaultTemplatesDirName.equals(".") ? "" : File.separator + defaultTemplatesDirName);
  }

  public String getName() {
    return myName;
  }

  @NotNull
  public Collection<FileTemplateBase> getAllTemplates(boolean includeDisabled) {
    List<FileTemplateBase> sorted = mySortedTemplates;
    if (sorted == null) {
      sorted = new ArrayList<FileTemplateBase>(myTemplates.values());
      Collections.sort(sorted, new Comparator<FileTemplateBase>() {
        @Override
        public int compare(FileTemplateBase t1, FileTemplateBase t2) {
          return t1.getName().compareToIgnoreCase(t2.getName());
        }
      });
      mySortedTemplates = sorted;
    }

    if (includeDisabled) {
      return Collections.unmodifiableCollection(sorted);
    }

    final List<FileTemplateBase> list = new ArrayList<FileTemplateBase>(sorted.size());
    for (FileTemplateBase template : sorted) {
      if (template instanceof BundledFileTemplate && !((BundledFileTemplate)template).isEnabled()) {
        continue;
      }
      list.add(template);
    }
    return list;
  }

  /**
   * @param templateQname
   * @return template no matter enabled or disabled it is
   */
  @Nullable
  public FileTemplateBase getTemplate(@NotNull String templateQname) {
    return myTemplates.get(templateQname);
  }

  /**
   * Disabled templates are never returned
   * @param templateName
   * @return
   */
  @Nullable
  public FileTemplateBase findTemplateByName(@NotNull String templateName) {
    final FileTemplateBase template = myTemplates.get(templateName);
    if (template != null) {
      final boolean isEnabled = !(template instanceof BundledFileTemplate) || ((BundledFileTemplate)template).isEnabled();
      if (isEnabled) {
        return template;
      }
    }
    // templateName must be non-qualified name, since previous lookup found nothing
    for (FileTemplateBase t : getAllTemplates(false)) {
      final String qName = t.getQualifiedName();
      if (qName.startsWith(templateName) && qName.charAt(templateName.length()) == '.') {
        return t;
      }
    }
    return null;
  }

  @NotNull
  public FileTemplateBase addTemplate(String name, String extension) {
    final String qName = FileTemplateBase.getQualifiedName(name, extension);
    FileTemplateBase template = getTemplate(qName);
    if (template == null) {
      template = new CustomFileTemplate(name, extension);
      myTemplates.put(qName, template);
      mySortedTemplates = null;
    }
    else {
      if (template instanceof BundledFileTemplate && !((BundledFileTemplate)template).isEnabled()) {
        ((BundledFileTemplate)template).setEnabled(true);
      }
    }
    return template;
  }

  public void removeTemplate(@NotNull String qName) {
    final FileTemplateBase template = myTemplates.get(qName);
    if (template instanceof CustomFileTemplate) {
      myTemplates.remove(qName);
      mySortedTemplates = null;
    }
    else if (template instanceof BundledFileTemplate){
      ((BundledFileTemplate)template).setEnabled(false);
    }
  }

  public void updateTemplates(Collection<FileTemplate> newTemplates) {
    final Set<String> toDisable = new HashSet<String>();
    for (DefaultTemplate template : myDefaultTemplates) {
      toDisable.add(template.getQualifiedName());
    }
    for (FileTemplate template : newTemplates) {
      toDisable.remove(((FileTemplateBase)template).getQualifiedName());
    }
    myTemplates.clear();
    mySortedTemplates = null;
    for (DefaultTemplate template : myDefaultTemplates) {
      final BundledFileTemplate bundled = createAndStoreBundledTemplate(template);
      if (toDisable.contains(bundled.getQualifiedName())) {
        bundled.setEnabled(false);
      }
    }
    for (FileTemplate template : newTemplates) {
      final FileTemplateBase _template = addTemplate(template.getName(), template.getExtension());
      _template.setText(template.getText());
      _template.setReformatCode(template.isReformatCode());
    }
  }

  public void addDefaultTemplate(DefaultTemplate template) {
    myDefaultTemplates.add(template);
    createAndStoreBundledTemplate(template);
  }

  private BundledFileTemplate createAndStoreBundledTemplate(DefaultTemplate template) {
    final BundledFileTemplate bundled = new BundledFileTemplate(template, myInternal);
    final String qName = bundled.getQualifiedName();
    final FileTemplateBase previous = myTemplates.put(qName, bundled);
    mySortedTemplates = null;

    LOG.assertTrue(previous == null, "Duplicate bundled template " + qName);
    return bundled;
  }

  void saveTemplates() {
    final File configRoot = getConfigRoot(true);

    final File[] files = configRoot.listFiles();

    final Set<String> allNames = new HashSet<String>();
    final Map<String, File> templatesOnDisk = files != null && files.length > 0? new HashMap<String, File>() : Collections.<String, File>emptyMap();
    if (files != null) {
      for (File file : files) {
        if (!file.isDirectory()) {
          final String name = file.getName();
          templatesOnDisk.put(name, file);
          allNames.add(name);
        }
      }
    }

    final Map<String, FileTemplateBase> templatesToSave = new HashMap<String, FileTemplateBase>();

    for (FileTemplateBase template : getAllTemplates(true)) {
      if (template instanceof BundledFileTemplate && !((BundledFileTemplate)template).isTextModified()) {
        continue;
      }
      final String name = template.getQualifiedName();
      templatesToSave.put(name, template);
      allNames.add(name);
    }

    if (!allNames.isEmpty()) {
      final String lineSeparator = CodeStyleSettingsManager.getSettings(ProjectManagerEx.getInstanceEx().getDefaultProject()).getLineSeparator();
      for (String name : allNames) {
        final File customizedTemplateFile = templatesOnDisk.get(name);
        final FileTemplateBase templateToSave = templatesToSave.get(name);
        if (customizedTemplateFile == null) {
          // template was not saved before
          try {
            saveTemplate(configRoot, templateToSave, lineSeparator);
          }
          catch (IOException e) {
            LOG.error("Unable to save template " + name, e);
          }
        }
        else if (templateToSave == null) {
          // template was removed
          FileUtil.delete(customizedTemplateFile);
        }
        else {
          // both customized content on disk and corresponding template are present
          try {
            final String diskText = StringUtil.convertLineSeparators(FileUtil.loadFile(customizedTemplateFile, CONTENT_ENCODING));
            final String templateText = templateToSave.getText();
            if (!diskText.equals(templateText)) {
              // save only if texts differ to avoid unnecessary file touching
              saveTemplate(configRoot, templateToSave, lineSeparator);
            }
          }
          catch (IOException e) {
            LOG.error("Unable to save template " + name, e);
          }
        }
      }
    }
  }

  /** Save template to file. If template is new, it is saved to specified directory. Otherwise it is saved to file from which it was read.
   *  If template was not modified, it is not saved.
   *  todo: review saving algorithm
   */
  private static void saveTemplate(File parentDir, FileTemplateBase template, final String lineSeparator) throws IOException {
    final File templateFile = new File(parentDir, template.getName() + "." + template.getExtension());

    FileOutputStream fileOutputStream;
    try {
      fileOutputStream = new FileOutputStream(templateFile);
    }
    catch (FileNotFoundException e) {
      // try to recover from the situation 'file exists, but is a directory'
      FileUtil.delete(templateFile);
      fileOutputStream = new FileOutputStream(templateFile);
    }
    OutputStreamWriter outputStreamWriter;
    try{
      outputStreamWriter = new OutputStreamWriter(fileOutputStream, CONTENT_ENCODING);
    }
    catch (UnsupportedEncodingException e){
      Messages.showMessageDialog(IdeBundle.message("error.unable.to.save.file.template.using.encoding", template.getName(),
                                                   CONTENT_ENCODING),
                                 CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      outputStreamWriter = new OutputStreamWriter(fileOutputStream);
    }
    String content = template.getText();

    if (!lineSeparator.equals("\n")){
      content = StringUtil.convertLineSeparators(content, lineSeparator);
    }

    outputStreamWriter.write(content);
    outputStreamWriter.close();
    fileOutputStream.close();
  }

  public File getConfigRoot(boolean create) {
    final File templatesPath = new File(PathManager.getConfigPath(), myTemplatesDir);
    if (create) {
      final boolean created = templatesPath.mkdirs();
      if (!created && !templatesPath.exists()) {
        LOG.info("Cannot create directory: " + templatesPath.getAbsolutePath());
      }
    }
    return templatesPath;
  }

  @Override
  public String toString() {
    return myName + " file template manager";
  }

}
