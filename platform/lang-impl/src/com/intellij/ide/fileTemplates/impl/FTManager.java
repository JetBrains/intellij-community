// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl;

import com.intellij.application.options.CodeStyle;
import com.intellij.configurationStore.StreamProvider;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.containers.MultiMap;
import kotlin.jvm.functions.Function3;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
final class FTManager {
  private static final Logger LOG = Logger.getInstance(FTManager.class);
  private static final String DEFAULT_TEMPLATE_EXTENSION = "ft";
  static final String TEMPLATE_EXTENSION_SUFFIX = "." + DEFAULT_TEMPLATE_EXTENSION;
  private static final String ENCODED_NAME_EXT_DELIMITER = "\u0F0Fext\u0F0F.";

  private final String name;
  private final boolean isInternal;
  private final Path templatePath;
  private final Path templateDir;
  private final @Nullable FTManager original;
  private final Map<String, FileTemplateBase> templates;
  private volatile List<FileTemplateBase> sortedTemplates;
  private final List<DefaultTemplate> defaultTemplates;
  private final StreamProvider streamProvider;

  FTManager(@NotNull @NonNls String name,
            @NotNull Path defaultTemplatesDirName,
            @NotNull Path templateDir,
            List<DefaultTemplate> defaultTemplates,
            boolean isInternal,
            StreamProvider streamProvider) {
    this.name = name;
    this.isInternal = isInternal;
    this.streamProvider = streamProvider;
    templatePath = defaultTemplatesDirName;
    this.templateDir = templateDir;
    original = null;
    this.defaultTemplates = defaultTemplates;
    templates = new HashMap<>(defaultTemplates.size());
    for (DefaultTemplate template : defaultTemplates) {
      BundledFileTemplate bundled = new BundledFileTemplate(template, this.isInternal);
      String qName = bundled.getQualifiedName();
      FileTemplateBase previous = templates.put(qName, bundled);
      if (previous != null) {
        LOG.warn("Duplicate bundled template " + qName + " [" + template + ", " + previous + ']');
      }
    }
  }

  FTManager(@NotNull FTManager original) {
    this.original = original;
    name = original.getName();
    templatePath = original.templatePath;
    templateDir = original.templateDir;
    isInternal = original.isInternal;
    templates = new HashMap<>(original.templates);
    defaultTemplates = List.copyOf(original.defaultTemplates);
    streamProvider = original.streamProvider;
  }

  @TestOnly
  FTManager(@NotNull @NonNls String name, @NotNull Path templateDir) {
    this(name, Path.of("test"), templateDir, Collections.emptyList(), false, FileTemplatesLoaderKt.streamProvider(null));
  }

  public @NotNull String getName() {
    return name;
  }

  @NotNull
  Collection<FileTemplateBase> getAllTemplates(boolean includeDisabled) {
    List<FileTemplateBase> sorted = sortedTemplates;
    if (sorted == null) {
      sorted = new ArrayList<>(getTemplates().values());
      sorted.sort((t1, t2) -> t1.getName().compareToIgnoreCase(t2.getName()));
      sortedTemplates = sorted;
    }

    if (includeDisabled) {
      return sorted;
    }

    List<FileTemplateBase> list = new ArrayList<>(sorted.size());
    for (FileTemplateBase template : sorted) {
      if (template instanceof BundledFileTemplate && !((BundledFileTemplate)template).isEnabled()) {
        continue;
      }
      list.add(template);
    }
    return list;
  }

  /**
   * @return template no matter enabled or disabled it is
   */
  @Nullable
  FileTemplateBase getTemplate(@NotNull String templateQname) {
    return getTemplates().get(templateQname);
  }

  /**
   * Disabled templates are never returned
   */
  public @Nullable FileTemplateBase findTemplateByName(@NotNull String templateName) {
    final FileTemplateBase template = getTemplates().get(templateName);
    if (template != null) {
      final boolean isEnabled = !(template instanceof BundledFileTemplate) || ((BundledFileTemplate)template).isEnabled();
      if (isEnabled) {
        return template;
      }
    }
    // templateName must be non-qualified name, since previous lookup found nothing
    for (FileTemplateBase t : getAllTemplates(false)) {
      final String qName = t.getQualifiedName();
      if (qName.startsWith(templateName) && qName.length() > templateName.length()) {
        String remainder = qName.substring(templateName.length());
        if (remainder.startsWith(ENCODED_NAME_EXT_DELIMITER) || remainder.charAt(0) == '.') {
          return t;
        }
      }
    }
    return null;
  }

  public @NotNull FileTemplateBase addTemplate(@NotNull String name, @NotNull String extension) {
    final String qName = FileTemplateBase.getQualifiedName(name, extension);
    FileTemplateBase template = getTemplate(qName);
    if (template == null) {
      template = new CustomFileTemplate(name, extension);
      getTemplates().put(qName, template);
      sortedTemplates = null;
    }
    else if (template instanceof BundledFileTemplate && !((BundledFileTemplate)template).isEnabled()) {
      ((BundledFileTemplate)template).setEnabled(true);
    }
    return template;
  }

  public void removeTemplate(@NotNull String qName) {
    final FileTemplateBase template = getTemplates().get(qName);
    if (template instanceof CustomFileTemplate) {
      getTemplates().remove(qName);
      sortedTemplates = null;
    }
    else if (template instanceof BundledFileTemplate){
      ((BundledFileTemplate)template).setEnabled(false);
    }
  }

  void updateTemplates(@NotNull Collection<? extends FileTemplate> newTemplates) {
    final Set<String> toDisable = new HashSet<>();
    for (DefaultTemplate template : defaultTemplates) {
      toDisable.add(template.getQualifiedName());
    }
    for (FileTemplate template : newTemplates) {
      toDisable.remove(((FileTemplateBase)template).getQualifiedName());
    }
    restoreDefaults(toDisable);
    MultiMap<String, FileTemplate> children = new MultiMap<>();
    for (FileTemplate template : newTemplates) {
      FileTemplateBase _template = addTemplate(template.getName(), template.getExtension());
      _template.setText(template.getText());
      _template.setFileName(template.getFileName());
      _template.setReformatCode(template.isReformatCode());
      _template.setLiveTemplateEnabled(template.isLiveTemplateEnabled());
      if (FileTemplateBase.isChild(_template)) {
        children.putValue(getParentName(_template), _template);
      }
    }
    for (String parentName : children.keySet()) {
      FileTemplateBase template = getTemplate(parentName);
      if (template != null) {
        template.setChildren(children.get(parentName).toArray(FileTemplate.EMPTY_ARRAY));
      }
    }
    saveTemplates(true);
  }

  private void restoreDefaults(@NotNull Set<String> toDisable) {
    Map<String, FileTemplateBase> templates = getTemplates();
    templates.clear();
    sortedTemplates = null;
    for (DefaultTemplate template : defaultTemplates) {
      BundledFileTemplate bundled = new BundledFileTemplate(template, isInternal);
      String qName = bundled.getQualifiedName();
      FileTemplateBase previous = templates.put(qName, bundled);
      if (previous != null) {
        LOG.error("Duplicate bundled template " + qName + " [" + template + ", " + previous + ']');
      }
      if (toDisable.contains(bundled.getQualifiedName())) {
        bundled.setEnabled(false);
      }
    }
  }

  public void loadCustomizedContent() {
    List<String> templateWithDefaultExtension = new ArrayList<>();
    Set<String> processedNames = new HashSet<>();
    List<FileTemplateBase> children = new ArrayList<>();
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (!processLocal((fileName, stream, aBoolean) -> {
      // check it here and not in filter to reuse fileName
      if (fileTypeManager.isFileIgnored(fileName)) {
        return true;
      }

      if (fileName.endsWith(TEMPLATE_EXTENSION_SUFFIX)) {
        templateWithDefaultExtension.add(fileName);
      }
      else {
        processedNames.add(fileName);
        FileTemplateBase template = addTemplateFromFile(fileName, stream);
        if (template != null && fileName.contains(FileTemplateBase.TEMPLATE_CHILDREN_SUFFIX)) {
          children.add(template);
        }
      }
      return true;
    })) {
      return;
    }

    for (FileTemplateBase child : children) {
      String qname = getParentName(child);
      FileTemplateBase parent = getTemplate(qname);
      if (parent != null) {
        parent.addChild(child);
      }
    }

    for (String fileName : templateWithDefaultExtension) {
      // cut default template extension
      String name = StringUtil.trimEnd(fileName, TEMPLATE_EXTENSION_SUFFIX);
      if (!processedNames.contains(name)) {
        addTemplateFromFile(name, null);
      }
      deleteFile(fileName);
    }
  }

  private void deleteFile(String fileName) {
    if (!streamProvider.delete(getSpec(fileName), RoamingType.DEFAULT)) {
      try {
        Files.delete(templateDir.resolve(fileName));
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  private boolean processLocal(Function3<String, InputStream, Boolean, Boolean> processor) {
    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(templateDir,
                                                                 file -> !Files.isDirectory(file) && !Files.isHidden(file))) {
      for (Path path : directoryStream) {
        try (InputStream stream = Files.newInputStream(path)) {
          processor.invoke(path.getFileName().toString(), stream, false);
        }
      }
    }
    catch (NoSuchFileException ignored) {
    }
    catch (IOException e) {
      LOG.error(e);
      return false;
    }
    return true;
  }

  private @NotNull @NonNls String getSpec(String fileName) {
    return FileUtil.toSystemIndependentName(templatePath.resolve(fileName).toString());
  }

  private static @NotNull String getParentName(FileTemplateBase child) {
    String name = child.getQualifiedName();
    return name.substring(0, name.indexOf(FileTemplateBase.TEMPLATE_CHILDREN_SUFFIX));
  }

  private @Nullable FileTemplateBase addTemplateFromFile(@NotNull String fileName, @Nullable InputStream stream) {
    Pair<String, String> nameExt = decodeFileName(fileName);
    final String extension = nameExt.second;
    final String templateQName = nameExt.first;
    if (templateQName.isEmpty()) {
      return null;
    }
    FileTemplateBase template = addTemplate(templateQName, extension);
    template.setText(stream != null ? readText(stream) : readFile(fileName));
    return template;
  }

  private String readFile(@NotNull String fileName) {
    try {
      return Files.readString(templateDir.resolve(fileName));
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  private static @Nullable String readText(@NotNull InputStream stream) {
    try {
      return StreamUtil.readText(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }

  public void saveTemplates() {
    saveTemplates(false);
  }

  private void saveTemplates(boolean removeDeleted) {
    final Set<String> allNames = new HashSet<>();
    final Set<String> templatesOnDisk = new HashSet<>();
    processLocal((name, stream, aBoolean) -> {
      templatesOnDisk.add(name);
      allNames.add(name);
      return true;
    });

    final Map<String, FileTemplateBase> templatesToSave = new HashMap<>();

    for (FileTemplateBase template : getAllTemplates(true)) {
      processTemplate(allNames, templatesToSave, template);
      for (FileTemplate child : template.getChildren()) {
        processTemplate(allNames, templatesToSave, (FileTemplateBase)child);
      }
    }

    if (allNames.isEmpty()) {
      return;
    }

    try {
      Files.createDirectories(templateDir);
    }
    catch (IOException e) {
      LOG.info("Cannot create directory: " + templateDir);
    }

    final String lineSeparator = CodeStyle.getDefaultSettings().getLineSeparator();
    for (String name : allNames) {
      final FileTemplateBase templateToSave = templatesToSave.get(name);
      if (!templatesOnDisk.contains(name)) {
        // template was not saved before
        saveTemplate(templateToSave, lineSeparator);
      }
      else if (templateToSave == null) {
        // template was removed
        if (removeDeleted) {
          deleteFile(name);
        }
      }
      else {
        // both customized content on disk and corresponding template are present
        String diskText = readFile(name);
        String templateText = templateToSave.getText();
        if (!templateText.equals(diskText)) {
          // save only if texts differ to avoid unnecessary file touching
          saveTemplate(templateToSave, lineSeparator);
        }
      }
    }
  }

  private static void processTemplate(Set<? super String> allNames, Map<String, FileTemplateBase> templatesToSave, FileTemplateBase template) {
    if (template instanceof BundledFileTemplate && !((BundledFileTemplate)template).isTextModified()) {
      return;
    }

    String name = template.getQualifiedName();
    templatesToSave.put(name, template);
    allNames.add(name);
  }

  /** Save template to file. If template is new, it is saved to specified directory. Otherwise, it is saved to file from which it was read.
   *  If template was not modified, it is not saved.
   */
  private void saveTemplate(@NotNull FileTemplate template, @NotNull String lineSeparator) {
    String fileName = encodeFileName(template.getName(), template.getExtension());
    String content = template.getText();
    if (!lineSeparator.equals("\n")) {
      content = StringUtilRt.convertLineSeparators(content, lineSeparator);
    }
    if (streamProvider.getEnabled()) {
      streamProvider.write(getSpec(fileName), content.getBytes(StandardCharsets.UTF_8), RoamingType.DEFAULT);
    }
    else {
      final Path templateFile = templateDir.resolve(fileName);
      try (OutputStream fileOutputStream = startWriteOrCreate(templateFile);
           OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8)) {
        outputStreamWriter.write(content);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  private static @NotNull OutputStream startWriteOrCreate(@NotNull Path templateFile) throws IOException {
    try {
      return Files.newOutputStream(templateFile);
    }
    catch (NoSuchFileException e) {
      // try to recover from the situation 'file exists, but is a directory'
      NioFiles.deleteRecursively(templateFile);
      return Files.newOutputStream(templateFile);
    }
  }

  @NotNull
  @TestOnly
  Path getConfigRoot() {
    return templateDir;
  }

  @Override
  public String toString() {
    return name + " file template manager";
  }

  public static @NotNull String encodeFileName(@NotNull String templateName, @NotNull String extension) {
    String nameExtDelimiter = extension.contains(".") ? ENCODED_NAME_EXT_DELIMITER : ".";
    return templateName + nameExtDelimiter + extension;
  }

  private static @NotNull Pair<String,String> decodeFileName(@NotNull String fileName) {
    String name = fileName;
    String ext = "";
    String nameExtDelimiter = fileName.contains(ENCODED_NAME_EXT_DELIMITER) ? ENCODED_NAME_EXT_DELIMITER : ".";
    int extIndex = fileName.lastIndexOf(nameExtDelimiter);
    if (extIndex >= 0) {
      name = fileName.substring(0, extIndex);
      ext = fileName.substring(extIndex + nameExtDelimiter.length());
    }
    return new Pair<>(name, ext);
  }

  public @NotNull Map<String, FileTemplateBase> getTemplates() {
    return original == null ? templates : original.templates;
  }
}
