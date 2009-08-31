package com.intellij.conversion.impl;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ConversionListener;
import com.intellij.conversion.ConversionService;
import com.intellij.conversion.ConverterProvider;
import com.intellij.conversion.impl.ui.ProjectConversionWizard;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.convert.ProjectConversionUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Document;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public class ConversionServiceImpl extends ConversionService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.conversion.impl.ConversionServiceImpl");

  @Override
  public boolean convertSilently(@NotNull String projectPath) {
    return convertSilently(projectPath, new ConversionListener() {
      public void conversionNeeded() {
      }

      public void successfullyConverted(File backupDir) {
      }

      public void error(String message) {
      }

      public void cannotWriteToFiles(List<File> readonlyFiles) {
      }
    });
  }

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
      context.saveFiles();
      saveConversionResult(context);
      return true;
    }
    catch (CannotConvertException e) {
      listener.error(e.getMessage());
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
      final List<ConversionRunner> converters = getConvertersToRun(context);
      final Iterator<ConversionRunner> iterator = converters.iterator();
      
      Set<String> convertersToRunIds = new HashSet<String>();
      while (iterator.hasNext()) {
        ConversionRunner runner = iterator.next();
        if (!runner.isConversionNeeded() && !convertersToRunIds.contains(runner.getProvider().getId())) {
          iterator.remove();
        }
        else {
          convertersToRunIds.add(runner.getProvider().getId());
        }
      }

      ProjectConversionWizard wizard = new ProjectConversionWizard(context, converters);
      wizard.show();
      if (wizard.isConverted()) {
        saveConversionResult(context);
        return true;
      }
      return false;
    }
    catch (CannotConvertException e) {
      LOG.info(e);
      Messages.showErrorDialog(IdeBundle.message("error.cannot.convert.project", e.getMessage()),
                               IdeBundle.message("title.cannot.convert.project"));
      return false;
    }
  }

  public boolean isConversionNeeded(String projectPath) throws CannotConvertException {
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

  private List<ConversionRunner> getConvertersToRun(final ConversionContextImpl context) throws CannotConvertException {
    final CachedConversionResult conversionResult = loadCachedConversionResult(context.getProjectFile());
    return createConversionRunners(context, conversionResult.myConverters);
  }

  private List<ConversionRunner> createConversionRunners(ConversionContextImpl context, final Set<String> performedConversionIds) {
    List<ConversionRunner> runners = new ArrayList<ConversionRunner>();
    final ConverterProvider[] providers = ConverterProvider.EP_NAME.getExtensions();
    for (ConverterProvider provider : providers) {
      if (!performedConversionIds.contains(provider.getId())) {
        runners.add(new ConversionRunner(provider, context));
      }
    }
    final CachingSemiGraph<ConverterProvider> graph = CachingSemiGraph.create(new ConverterProvidersGraph(providers));
    final DFSTBuilder<ConverterProvider> builder = new DFSTBuilder<ConverterProvider>(GraphGenerator.create(graph));
    if (!builder.isAcyclic()) {
      final Pair<ConverterProvider,ConverterProvider> pair = builder.getCircularDependency();
      LOG.error("cyclic dependencies between converters: " + pair.getFirst().getId() + " and " + pair.getSecond().getId());
    }
    final Comparator<ConverterProvider> comparator = builder.comparator();
    Collections.sort(runners, new Comparator<ConversionRunner>() {
      public int compare(ConversionRunner o1, ConversionRunner o2) {
        return comparator.compare(o1.getProvider(), o2.getProvider());
      }
    });
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

  public boolean convertModule(@NotNull final Project project, @NotNull final File moduleFile) {
    final IProjectStore stateStore = ((ProjectImpl)project).getStateStore();
    String projectPath = FileUtil.toSystemDependentName(stateStore.getProjectFilePath());
    if (stateStore.getStorageScheme() != StorageScheme.DEFAULT) {
      projectPath = new File(projectPath).getParent();
    }

    if (!isConversionNeeded(projectPath, moduleFile)) {
      return false;
    }

    final int res = Messages.showYesNoDialog(project, IdeBundle.message("message.module.file.has.an.older.format.do.you.want.to.convert.it"),
                                             IdeBundle.message("dialog.title.convert.module"), Messages.getQuestionIcon());
    if (res != 0) {
      return false;
    }
    if (!moduleFile.canWrite()) {
      Messages.showErrorDialog(project, IdeBundle.message("error.message.cannot.modify.file.0", moduleFile.getAbsolutePath()),
                               IdeBundle.message("dialog.title.convert.module"));
      return false;
    }

    try {
      ConversionContextImpl context = new ConversionContextImpl(projectPath);
      final List<ConversionRunner> runners = createConversionRunners(context, Collections.<String>emptySet());
      final File backupFile = ProjectConversionUtil.backupFile(moduleFile);
      for (ConversionRunner runner : runners) {
        if (runner.isModuleConversionNeeded(moduleFile)) {
          runner.convertModule(moduleFile);
        }
      }
      context.saveFiles();
      Messages.showInfoMessage(project, IdeBundle.message("message.your.module.was.succesfully.converted.br.old.version.was.saved.to.0", backupFile.getAbsolutePath()),
                               IdeBundle.message("dialog.title.convert.module"));
      return true;
    }
    catch (CannotConvertException e) {
      Messages.showErrorDialog(IdeBundle.message("error.cannot.load.project", e.getMessage()), "Cannot Convert Module");
      return false;
    }
    catch (IOException e) {
      return false;
    }
  }

  private boolean isConversionNeeded(String projectPath, File moduleFile) {
    try {
      ConversionContextImpl context = new ConversionContextImpl(projectPath);
      final List<ConversionRunner> runners = createConversionRunners(context, Collections.<String>emptySet());
      for (ConversionRunner runner : runners) {
        if (runner.isModuleConversionNeeded(moduleFile)) {
          return true;
        }
      }
      return false;
    }
    catch (CannotConvertException e) {
      LOG.info(e);
      return false;
    }
  }

  public static class CachedConversionResult {
    @Tag("converters")
    @AbstractCollection(surroundWithTag = false, elementTag = "converter", elementValueAttribute = "id")
    public Set<String> myConverters = new HashSet<String>();

    @Tag("not-converted-modules")
    @AbstractCollection(surroundWithTag = false, elementTag = "module", elementValueAttribute = "path")
    public Set<String> myNotConvertedModules = new HashSet<String>();
  }

  private class ConverterProvidersGraph implements GraphGenerator.SemiGraph<ConverterProvider> {
    private final ConverterProvider[] myProviders;

    public ConverterProvidersGraph(ConverterProvider[] providers) {
      myProviders = providers;
    }

    public Collection<ConverterProvider> getNodes() {
      return Arrays.asList(myProviders);
    }

    public Iterator<ConverterProvider> getIn(ConverterProvider n) {
      List<ConverterProvider> preceding = new ArrayList<ConverterProvider>();
      for (String id : n.getPrecedingConverterIds()) {
        for (ConverterProvider provider : myProviders) {
          if (provider.getId().equals(id)) {
            preceding.add(provider);
          }
        }
      }
      return preceding.iterator();
    }
  }
}
