/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.impl.convert;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.convert.ui.ProjectConversionWizard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public class ProjectConversionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.impl.convert.ProjectConversionUtil");
  @NonNls private static final String PROJECT_FILES_BACKUP = "projectFilesBackup";
  @NonNls private static final String BACKUP_EXTENSION = "backup";

  private ProjectConversionUtil() {
  }

  private static boolean isConverted(Element versionComponent) {
    return Boolean.parseBoolean(versionComponent.getAttributeValue(ProjectFileVersionImpl.CONVERTED_ATTRIBUTE));
  }

  private static Element loadProjectFileRoot(String path) throws QualifiedJDomException, IOException {
    final Document document = JDomConvertingUtil.loadDocument(new File(FileUtil.toSystemDependentName(path)));
    return document.getRootElement();
  }

  public static boolean convertSilently(String projectFilePath, ConversionListener listener) {
    try {
      Element versionComponent = getVersionComponent(projectFilePath);
      if (versionComponent != null && isConverted(versionComponent)) return true;

      final ProjectConverter converter = getConverter(projectFilePath);
      if (converter == null) return true;

      converter.prepare();
      if (!converter.isConversionNeeded()) return true;

      listener.conversionNeeded();
      final List<File> readonlyFiles = getReadonlyFiles(converter);
      if (!readonlyFiles.isEmpty()) {
        listener.cannotWriteToFiles(readonlyFiles);
        return false;
      }

      final File file = backupAndConvert(converter);
      listener.succesfullyConverted(file);
      return true;
    }
    catch (QualifiedJDomException e) {
      listener.error(e.getFilePath() + ": " + e.getCause().getMessage());
    }
    catch (IOException e) {
      listener.error(e.getMessage());
    }
    return false;
  }

  @NotNull
  public static ProjectConversionResult convertProject(String projectFilePath) {
    try {
      final Element versionComponent = getVersionComponent(projectFilePath);
      if (versionComponent != null && isConverted(versionComponent)) {
        return ProjectConversionResult.OK;
      }

      final ProjectConverter converter = getConverter(projectFilePath);
      if (converter == null) {
        return ProjectConversionResult.OK;
      }

      converter.prepare();
      if (!converter.isConversionNeeded()) {
        return ProjectConversionResult.OK;
      }

      if (versionComponent != null) {
        LOG.assertTrue(!isConverted(versionComponent));
        return new ProjectConversionResult(converter.createHelper());
      }

      String projectName = FileUtil.getNameWithoutExtension(new File(projectFilePath));
      ProjectConversionWizard wizard = new ProjectConversionWizard(converter, projectName);
      wizard.show();
      if (!wizard.isOK()) {
        return ProjectConversionResult.DO_NOT_OPEN;
      }

      if (wizard.isConverted()) {
        return ProjectConversionResult.OK;
      }

      return new ProjectConversionResult(converter.createHelper());
    }
    catch (IOException e) {
      Messages.showErrorDialog(IdeBundle.message("error.cannot.load.project", e.getMessage()),
                               IdeBundle.message("title.cannot.convert.project"));
      return ProjectConversionResult.DO_NOT_OPEN;
    }
    catch (QualifiedJDomException e) {
      LOG.info(e);
      Messages.showErrorDialog(IdeBundle.message("error.some.file.is.corrupted.message", e.getFilePath(), e.getCause().getMessage()),
                               IdeBundle.message("title.cannot.convert.project"));
      return ProjectConversionResult.DO_NOT_OPEN;
    }

  }

  @Nullable
  private static Element getVersionComponent(final String projectFilePath) throws QualifiedJDomException, IOException {
    return JDomConvertingUtil.findComponent(loadProjectFileRoot(projectFilePath), ProjectFileVersionImpl.COMPONENT_NAME);
  }

  @Nullable
  private static ProjectConverter getConverter(final String projectFilePath) {
    for (ConverterFactory converterFactory : Extensions.getExtensions(ConverterFactory.EXTENSION_POINT)) {
      final ProjectConverter converter = converterFactory.createConverter(projectFilePath);
      if (converter != null) {
        return converter;
      }
    }
    return null;
  }

  @Nullable
  public static ModuleConverter getModuleConverter() {
    for (ConverterFactory converterFactory : Extensions.getExtensions(ConverterFactory.EXTENSION_POINT)) {
      final ModuleConverter converter = converterFactory.createModuleConverter();
      if (converter != null) {
        return converter;
      }
    }
    return null;
  }

  public static File backupFile(File file) throws IOException {
    final String fileName = FileUtil.createSequentFileName(file.getParentFile(), file.getName(), BACKUP_EXTENSION);
    final File backup = new File(file.getParentFile(), fileName);
    FileUtil.copy(file, backup);
    return backup; 
  }

  public static File backupFiles(final File[] files, final File parentDir) throws IOException {
    final String dirName = FileUtil.createSequentFileName(parentDir, PROJECT_FILES_BACKUP, "");
    File backupDir = new File(parentDir, dirName);
    backupDir.mkdirs();
    for (File file : files) {
      FileUtil.copy(file, new File(backupDir, file.getName()));
    }
    return backupDir;
  }

  public static File backupAndConvert(final ProjectConverter converter) throws IOException, QualifiedJDomException {
    final File[] files = converter.getAffectedFiles();
    final File parentDir = converter.getBaseDirectory();
    File backupDir = backupFiles(files, parentDir);
    converter.convert();
    return backupDir;
  }

  public static List<File> getReadonlyFiles(final ProjectConverter converter) {
    final File[] files = converter.getAffectedFiles();
    List<File> readonlyFiles = new ArrayList<File>();
    for (File file : files) {
      if (!file.canWrite()) {
        readonlyFiles.add(file);
      }
    }
    return readonlyFiles;
  }

  public static class ProjectConversionResult {
    public static final ProjectConversionResult OK = new ProjectConversionResult(false, null);
    public static final ProjectConversionResult DO_NOT_OPEN = new ProjectConversionResult(true, null);

    private boolean myOpeningCancelled;
    private ProjectConversionHelper myConversionHelper;

    public ProjectConversionResult(ProjectConversionHelper helper) {
      this(false, helper);
    }

    private ProjectConversionResult(final boolean openingCancelled, final ProjectConversionHelper conversionHelper) {
      myOpeningCancelled = openingCancelled;
      myConversionHelper = conversionHelper;
    }

    public boolean isOpeningCancelled() {
      return myOpeningCancelled;
    }

    public ProjectConversionHelper getConversionHelper() {
      return myConversionHelper;
    }
  }

  public interface ConversionListener {
    void conversionNeeded();
    void succesfullyConverted(File backupDir);
    void error(String message);
    void cannotWriteToFiles(final List<File> readonlyFiles);
  }
}
