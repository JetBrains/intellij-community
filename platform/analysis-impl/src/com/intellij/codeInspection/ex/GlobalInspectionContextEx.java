// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.google.common.collect.Lists;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.DefaultInspectionToolResultExporter;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionToolResultExporter;
import com.intellij.codeInspection.InspectionsResultUtil;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.codeInspection.ui.AggregateResultsExporter;
import com.intellij.codeInspection.ui.GlobalReportedProblemFilter;
import com.intellij.codeInspection.ui.ReportedProblemFilter;
import com.intellij.configurationStore.JbXmlOutputter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class GlobalInspectionContextEx extends GlobalInspectionContextBase {
  private static final Logger LOG = Logger.getInstance(GlobalInspectionContextEx.class);
  private static final int MAX_OPEN_GLOBAL_INSPECTION_XML_RESULT_FILES = SystemProperties
    .getIntProperty("max.open.global.inspection.xml.files", 50);
  private final ConcurrentMap<InspectionToolWrapper<?, ?>, InspectionToolResultExporter> myPresentationMap = new ConcurrentHashMap<>();
  protected volatile Path myOutputDir;
  protected GlobalReportedProblemFilter myGlobalReportedProblemFilter;
  private ReportedProblemFilter myReportedProblemFilter;
  Map<Path, Long> myProfile;

  public GlobalInspectionContextEx(@NotNull Project project) {super(project);}

  public void launchInspectionsOffline(@NotNull AnalysisScope scope,
                                       @NotNull Path outputPath,
                                       boolean runGlobalToolsOnly,
                                       @NotNull List<? super Path> inspectionsResults) {
    performInspectionsWithProgressAndExportResults(scope, runGlobalToolsOnly, true, outputPath, inspectionsResults);
  }

  public void performInspectionsWithProgressAndExportResults(final @NotNull AnalysisScope scope,
                                                             final boolean runGlobalToolsOnly,
                                                             final boolean isOfflineInspections,
                                                             @NotNull Path outputDir,
                                                             final @NotNull List<? super Path> inspectionsResults) {
    cleanupTools();
    setCurrentScope(scope);

    final Runnable action = () -> {
      myOutputDir = outputDir;
      try {
        performInspectionsWithProgress(scope, runGlobalToolsOnly, isOfflineInspections);
        exportResultsSmart(inspectionsResults, outputDir);
      }
      finally {
        myOutputDir = null;
      }
    };
    action.run();
  }

  protected void exportResults(@NotNull List<? super Path> inspectionsResults,
                               @NotNull List<? extends Tools> inspections,
                               @NotNull Path outputDir,
                               @Nullable XMLOutputFactory xmlOutputFactory) {
    if (xmlOutputFactory == null) {
      xmlOutputFactory = XMLOutputFactory.newInstance();
    }

    BufferedWriter[] writers = new BufferedWriter[inspections.size()];
    XMLStreamWriter[] xmlWriters = new XMLStreamWriter[inspections.size()];

    try {
      int i = 0;
      for (Tools inspection : inspections) {
        inspectionsResults.add(InspectionsResultUtil.getInspectionResultPath(outputDir, inspection.getShortName()));
        try {
          BufferedWriter writer = InspectionsResultUtil.getWriter(outputDir, inspection.getShortName());
          writers[i] = writer;
          XMLStreamWriter xmlWriter = xmlOutputFactory.createXMLStreamWriter(writer);
          xmlWriters[i++] = xmlWriter;
          xmlWriter.writeStartElement(GlobalInspectionContextBase.PROBLEMS_TAG_NAME);
          xmlWriter.writeCharacters("\n");
          xmlWriter.flush();
        }
        catch (IOException | XMLStreamException e) {
          LOG.error(e);
        }
      }

      getRefManager().iterate(new RefVisitor() {
        @Override
        public void visitElement(final @NotNull RefEntity refEntity) {
          int i = 0;
          for (Tools tools : inspections) {
            for (ScopeToolState state : tools.getTools()) {
              try {
                InspectionToolWrapper<?, ?> toolWrapper = state.getTool();
                InspectionToolResultExporter presentation = getPresentation(toolWrapper);
                BufferedWriter writer = writers[i];
                if (writer != null &&
                    (myGlobalReportedProblemFilter == null ||
                     myGlobalReportedProblemFilter.shouldReportProblem(refEntity, toolWrapper.getShortName()))) {
                  presentation.exportResults(e -> {
                    try {
                      JbXmlOutputter.collapseMacrosAndWrite(e, getProject(), writer);
                      writer.flush();
                    }
                    catch (IOException e1) {
                      throw new RuntimeException(e1);
                    }
                  }, refEntity, d -> false);
                }
              }
              catch (Throwable e) {
                LOG.error("Problem when exporting: " + refEntity.getExternalName(), e);
              }
            }
            i++;
          }
        }
      });

      for (XMLStreamWriter xmlWriter : xmlWriters) {
        if (xmlWriter != null) {
          try {
            xmlWriter.writeEndElement();
            xmlWriter.flush();
          }
          catch (XMLStreamException e) {
            LOG.error(e);
          }
        }
      }
    }
    finally {
      for (BufferedWriter writer : writers) {
        if (writer != null) {
          try {
            writer.close();
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      }
    }
  }

  public void exportResultsSmart(@NotNull List<? super Path> inspectionsResults, @NotNull Path outputDir) {
    final List<Tools> globalToolsWithProblems = new ArrayList<>();
    final List<Tools> toolsWithResultsToAggregate = new ArrayList<>();
    for (Map.Entry<String, Tools> entry : getTools().entrySet()) {
      final Tools sameTools = entry.getValue();
      boolean hasProblems = false;
      String toolName = entry.getKey();
      if (sameTools != null) {
        for (ScopeToolState toolDescr : sameTools.getTools()) {
          InspectionToolWrapper<?, ?> toolWrapper = toolDescr.getTool();
          InspectionToolResultExporter presentation = getPresentation(toolWrapper);
          if (presentation instanceof AggregateResultsExporter) {
            presentation.updateContent();
            if (presentation.hasReportedProblems()) {
              toolsWithResultsToAggregate.add(sameTools);
              break;
            }
          }
          if (toolWrapper instanceof LocalInspectionToolWrapper) {
            hasProblems = Files.exists(InspectionsResultUtil.getInspectionResultFile(outputDir, toolWrapper.getShortName()));
          }
          else {
            presentation.updateContent();
            if (presentation.hasReportedProblems()) {
              globalToolsWithProblems.add(sameTools);
              LOG.assertTrue(!hasProblems, toolName);
              break;
            }
          }
        }
      }

      // close "problem" tag for local inspections (see DefaultInspectionToolResultExporter.addProblemElement())
      if (hasProblems) {
        try {
          final Path file = InspectionsResultUtil.getInspectionResultPath(outputDir, sameTools.getShortName());
          inspectionsResults.add(file);
          Files.write(file, ("</" + PROBLEMS_TAG_NAME + ">").getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }

    exportResultsWithAggregation(inspectionsResults, toolsWithResultsToAggregate, myOutputDir);

    // export global inspections
    if (!globalToolsWithProblems.isEmpty()) {
      XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
      Lists.partition(globalToolsWithProblems, MAX_OPEN_GLOBAL_INSPECTION_XML_RESULT_FILES).forEach(inspections ->
                                                                                                      exportResults(inspectionsResults,
                                                                                                                    inspections, outputDir,
                                                                                                                    xmlOutputFactory));
    }
  }

  private void exportResultsWithAggregation(@NotNull List<? super Path> inspectionsResults,
                                            @NotNull List<? extends Tools> toolsWithResultsToAggregate,
                                            @NotNull Path outputPath) {
    for (Tools tools : toolsWithResultsToAggregate) {
      String inspectionName = tools.getShortName();
      inspectionsResults.add(InspectionsResultUtil.getInspectionResultFile(outputPath, inspectionName));
      inspectionsResults.add(InspectionsResultUtil.getInspectionResultFile(outputPath, inspectionName + InspectionsResultUtil.AGGREGATE));
      try {
        List<? extends InspectionToolWrapper<?, ?>> wrappers = ContainerUtil.map(tools.getTools(), ScopeToolState::getTool);
        InspectionsResultUtil.writeInspectionResult(getProject(), inspectionName, wrappers, outputPath, this::getPresentation);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  public @NotNull InspectionToolResultExporter getPresentation(@NotNull InspectionToolWrapper<?, ?> toolWrapper) {
    InspectionToolResultExporter presentation = myPresentationMap.get(toolWrapper);
    if (presentation == null) {
      presentation = createPresentation(toolWrapper);

      presentation = ConcurrencyUtil.cacheOrGet(myPresentationMap, toolWrapper, presentation);
    }
    return presentation;
  }

  protected @NotNull InspectionToolResultExporter createPresentation(@NotNull InspectionToolWrapper<?, ?> toolWrapper) {
    String presentationClass = StringUtil
      .notNullize(toolWrapper.myEP == null ? null : toolWrapper.myEP.presentation, DefaultInspectionToolResultExporter.class.getName());

    try {
      InspectionToolResultExporter presentation;
      InspectionEP extension = toolWrapper.getExtension();
      ClassLoader classLoader = extension == null ? getClass().getClassLoader() : extension.getPluginDescriptor().getPluginClassLoader();
      Constructor<?> constructor = Class.forName(presentationClass, true, classLoader)
        .getConstructor(InspectionToolWrapper.class, GlobalInspectionContextEx.class);
      presentation = (InspectionToolResultExporter)constructor.newInstance(toolWrapper, this);
      return presentation;
    }
    catch (Exception e) {
      LOG.error(e);
      throw new RuntimeException(e);
    }
  }

  public ReportedProblemFilter getReportedProblemFilter() {
    return myReportedProblemFilter;
  }

  public void setReportedProblemFilter(ReportedProblemFilter reportedProblemFilter) {
    myReportedProblemFilter = reportedProblemFilter;
  }

  public GlobalReportedProblemFilter getGlobalReportedProblemFilter() {
    return myGlobalReportedProblemFilter;
  }

  public void setGlobalReportedProblemFilter(GlobalReportedProblemFilter reportedProblemFilter) {
    myGlobalReportedProblemFilter = reportedProblemFilter;
  }

  public @Nullable Path getOutputPath() {
    return myOutputDir;
  }

  public void startPathProfiling() {
    myProfile = new ConcurrentHashMap<>();
  }

  public Map<Path, Long> getPathProfile() {
    return myProfile;
  }

  void updateProfile(VirtualFile virtualFile, long millis) {
    if (myProfile != null) {
      Path path = Paths.get(virtualFile.getPath());
      myProfile.merge(path, millis, Long::sum);
    }
  }
}
