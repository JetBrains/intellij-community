package com.intellij.conversion.impl;

import com.intellij.conversion.ConversionListener;
import com.intellij.conversion.ConversionService;
import com.intellij.conversion.ConverterProvider;
import com.intellij.conversion.impl.ui.ProjectConversionWizard;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.convert.QualifiedJDomException;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Document;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class ConversionServiceImpl extends ConversionService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.conversion.impl.ConversionServiceImpl");

  @Override
  public boolean convertSilently(@NotNull String projectPath, @NotNull ConversionListener listener) {
    try {
      if (!isConversionNeeded(projectPath)) {
        return true;
      }

      listener.conversionNeeded();
      ConversionContextImpl context = new ConversionContextImpl(projectPath);
      for (ConversionRunner runner : getConvertersToRun(context)) {
        if (runner.isConversionNeeded()) {
          final List<File> readOnlyFiles = ConversionRunner.getReadOnlyFiles(runner.getAffectedFiles());
          if (!readOnlyFiles.isEmpty()) {
            listener.cannotWriteToFiles(readOnlyFiles);
            return false;
          }
          runner.preProcess();
          runner.process();
          runner.postProcess();
        }
      }
      saveConversionResult(context);
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

  @Override
  public boolean convert(@NotNull String projectPath) {
    try {
      if (!isConversionNeeded(projectPath)) {
        return true;
      }

      final ConversionContextImpl context = new ConversionContextImpl(projectPath);
      ProjectConversionWizard wizard = new ProjectConversionWizard(context, getConvertersToRun(context));
      wizard.show();
      if (wizard.isConverted()) {
        saveConversionResult(context);
        return true;
      }
      return false;
    }
    catch (IOException e) {
      Messages.showErrorDialog(IdeBundle.message("error.cannot.load.project", e.getMessage()),
                               IdeBundle.message("title.cannot.convert.project"));
      return false;
    }
    catch (QualifiedJDomException e) {
      LOG.info(e);
      Messages.showErrorDialog(IdeBundle.message("error.some.file.is.corrupted.message", e.getFilePath(), e.getCause().getMessage()),
                               IdeBundle.message("title.cannot.convert.project"));
      return false;
    }
  }

  private boolean isConversionNeeded(String projectPath) throws IOException, QualifiedJDomException {
    final ConversionContextImpl context = new ConversionContextImpl(projectPath);
    final List<ConversionRunner> runners = getConvertersToRun(context);
    if (runners.isEmpty()) {
      return false;
    }
    for (ConversionRunner runner : runners) {
      if (runner.isConversionNeeded()) {
        return true;
      }
    }
    saveConversionResult(context);
    return false;
  }

  private List<ConversionRunner> getConvertersToRun(final ConversionContextImpl context) throws IOException, QualifiedJDomException {
    final CachedConversionResult conversionResult = loadCachedConversionResult(context.getProjectFile());

    List<ConversionRunner> runners = new ArrayList<ConversionRunner>();
    for (ConverterProvider provider : ConverterProvider.EP_NAME.getExtensions()) {
      if (!conversionResult.myConverters.contains(provider.getId())) {
        runners.add(new ConversionRunner(provider, context));
      }
    }
    return runners;
  }

  private void saveConversionResult(ConversionContextImpl context) {
    final CachedConversionResult conversionResult = loadCachedConversionResult(context.getProjectFile());
    for (ConverterProvider provider : ConverterProvider.EP_NAME.getExtensions()) {
      conversionResult.myConverters.add(provider.getId());
    }
    for (File file : context.getNonExistingModuleFiles()) {
      conversionResult.myNotConvertedModules.add(file.getAbsolutePath());
    }
    final File infoFile = getConversionInfoFile(context.getProjectFile());
    try {
      JDOMUtil.writeDocument(new Document(XmlSerializer.serialize(conversionResult)), infoFile, SystemProperties.getLineSeparator());
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  @NotNull
  private CachedConversionResult loadCachedConversionResult(File projectFile) {
    try {
      final File infoFile = getConversionInfoFile(projectFile);
      if (!infoFile.exists()) {
        return new CachedConversionResult();
      }
      final Document document = JDOMUtil.loadDocument(infoFile);
      final CachedConversionResult result = XmlSerializer.deserialize(document, CachedConversionResult.class);
      return result != null ? result : new CachedConversionResult();
    }
    catch (Exception e) {
      LOG.info(e);
      return new CachedConversionResult();
    }
  }

  private File getConversionInfoFile(@NotNull File projectFile) {
    String dirName = PathUtil.suggestFileName(projectFile.getName() + Integer.toHexString(projectFile.getAbsolutePath().hashCode()));
    return new File(PathManager.getSystemPath() + File.separator + "conversion" + File.separator + dirName + ".xml");
  }

  public static class CachedConversionResult {
    @Tag("converters")
    @AbstractCollection(surroundWithTag = false, elementTag = "converter", elementValueAttribute = "id")
    public Set<String> myConverters = new HashSet<String>();

    @Tag("not-converted-modules")
    @AbstractCollection(surroundWithTag = false, elementTag = "module", elementValueAttribute = "path")
    public Set<String> myNotConvertedModules = new HashSet<String>();
  }
}
