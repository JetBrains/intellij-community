// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
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

  @Topic.ProjectLevel
  public static final Topic<InspectListener> INSPECT_TOPIC = new Topic<>(InspectListener.class, Topic.BroadcastDirection.NONE);

  private static final Logger LOG = Logger.getInstance(GlobalInspectionContextEx.class);
  private static final int MAX_OPEN_GLOBAL_INSPECTION_XML_RESULT_FILES = SystemProperties
    .getIntProperty("max.open.global.inspection.xml.files", 50);
  private final ConcurrentMap<InspectionToolWrapper<?, ?>, InspectionToolResultExporter> myPresentationMap = new ConcurrentHashMap<>();
  private volatile Path myOutputDir;
  private GlobalReportedProblemFilter myGlobalReportedProblemFilter;
  private ReportedProblemFilter myReportedProblemFilter;
  private Map<Path, Long> myProfile;
  protected InspectionProblemConsumer myProblemConsumer;

  public GlobalInspectionContextEx(@NotNull Project project) { super(project); }

  public void launchInspectionsOffline(@NotNull AnalysisScope scope,
                                       @NotNull Path outputPath,
                                       boolean runGlobalToolsOnly,
                                       @NotNull List<? super Path> inspectionsResults) {
    performInspectionsWithProgressAndExportResults(scope, runGlobalToolsOnly, true, outputPath, inspectionsResults);
  }

  public void performInspectionsWithProgressAndExportResults(@NotNull AnalysisScope scope,
                                                             boolean runGlobalToolsOnly,
                                                             boolean isOfflineInspections,
                                                             @NotNull Path outputDir,
                                                             @NotNull List<? super Path> inspectionsResults) {
    cleanupTools();
    setCurrentScope(scope);

    myOutputDir = outputDir;
    try {
      try {
        performInspectionsWithProgress(scope, runGlobalToolsOnly, isOfflineInspections);
      }
      finally {
        if (areToolsInitialized()) {
          exportResultsSmart(inspectionsResults, outputDir);
        }
      }
    }
    finally {
      myOutputDir = null;
    }
  }

  private void exportResults(@NotNull List<? super Path> inspectionsResults,
                             @NotNull List<? extends Tools> inspections,
                             @NotNull Path outputDir,
                             @Nullable XMLOutputFactory xmlOutputFactory) {
    if (xmlOutputFactory == null) {
      xmlOutputFactory = XMLOutputFactory.newDefaultFactory();
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

      List<List<ScopeToolState>> states = new ArrayList<>();
      for (Tools inspection : inspections) {
        states.add(inspection.getTools());
      }
      getRefManager().iterate(new RefVisitor() {
        @Override
        public void visitElement(@NotNull RefEntity refEntity) {
          for (int i = 0; i < states.size(); i++) {
            for (ScopeToolState state : states.get(i)) {
              try {
                InspectionToolWrapper<?, ?> toolWrapper = state.getTool();
                BufferedWriter writer = writers[i];
                if (writer != null &&
                    (myGlobalReportedProblemFilter == null ||
                     myGlobalReportedProblemFilter.shouldReportProblem(refEntity, toolWrapper.getShortName()))) {
                  getPresentation(toolWrapper).exportResults(e -> {
                    try {
                      JbXmlOutputter.Companion.collapseMacrosAndWrite(e, getProject(), writer);
                      writer.flush();
                    }
                    catch (IOException e1) {
                      throw new RuntimeException(e1);
                    }
                  }, refEntity, d -> false);
                }
                else {
                  return;
                }
              }
              catch (Throwable e) {
                LOG.error("Problem when exporting: " + refEntity.getExternalName(), e);
              }
            }
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

  private void exportResultsSmart(@NotNull List<? super Path> inspectionsResults, @NotNull Path outputDir) {
    List<Tools> globalToolsWithProblems = new ArrayList<>();
    List<Tools> toolsWithResultsToAggregate = new ArrayList<>();
    for (Map.Entry<String, Tools> entry : getTools().entrySet()) {
      Tools sameTools = entry.getValue();
      boolean hasProblems = false;
      String toolName = entry.getKey();
      if (sameTools != null) {
        for (ScopeToolState toolDescr : sameTools.getTools()) {
          InspectionToolWrapper<?, ?> toolWrapper = toolDescr.getTool();
          InspectionToolResultExporter presentation = getPresentation(toolWrapper);
          try {
            if (presentation instanceof AggregateResultsExporter) {
              ReadAction.run(() -> presentation.updateContent());
              if (presentation.hasReportedProblems().toBoolean()) {
                toolsWithResultsToAggregate.add(sameTools);
                break;
              }
            }
            if (toolWrapper instanceof LocalInspectionToolWrapper) {
              hasProblems = Files.exists(InspectionsResultUtil.getInspectionResultPath(outputDir, toolWrapper.getShortName()));
            }
            else {
              ReadAction.run(() -> presentation.updateContent());
              if (presentation.hasReportedProblems().toBoolean()) {
                globalToolsWithProblems.add(sameTools);
                LOG.assertTrue(!hasProblems, toolName);
                break;
              }
            }
          }
          catch (ProcessCanceledException | IndexNotReadyException e) {
            throw e;
          }
          catch (Throwable e) {
            LOG.error(e);
          }
        }
      }

      // close "problem" tag for local inspections (see DefaultInspectionToolResultExporter.addProblemElement())
      if (hasProblems) {
        try {
          Path file = InspectionsResultUtil.getInspectionResultPath(outputDir, sameTools.getShortName());
          inspectionsResults.add(file);
          Files.writeString(file, "</" + PROBLEMS_TAG_NAME + ">", StandardOpenOption.APPEND);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }

    exportResultsWithAggregation(inspectionsResults, toolsWithResultsToAggregate, outputDir);

    // export global inspections
    if (!globalToolsWithProblems.isEmpty()) {
      XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newDefaultFactory();
      ContainerUtil.splitListToChunks(globalToolsWithProblems, MAX_OPEN_GLOBAL_INSPECTION_XML_RESULT_FILES)
        .forEach(inspections -> exportResults(inspectionsResults, inspections, outputDir, xmlOutputFactory));
    }
  }

  private void exportResultsWithAggregation(@NotNull List<? super Path> inspectionsResults,
                                            @NotNull List<? extends Tools> toolsWithResultsToAggregate,
                                            @NotNull Path outputPath) {
    for (Tools tools : toolsWithResultsToAggregate) {
      String inspectionName = tools.getShortName();
      inspectionsResults.add(InspectionsResultUtil.getInspectionResultPath(outputPath, inspectionName));
      inspectionsResults.add(InspectionsResultUtil.getInspectionResultPath(outputPath, inspectionName + InspectionsResultUtil.AGGREGATE));
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

  public void setProblemConsumer(@NotNull InspectionProblemConsumer problemConsumer) {
    myProblemConsumer = problemConsumer;
  }
}
