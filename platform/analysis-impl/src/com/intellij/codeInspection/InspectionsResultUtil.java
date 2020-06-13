// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ui.AggregateResultsExporter;
import com.intellij.configurationStore.JbXmlOutputter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.JBIterable;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

public final class InspectionsResultUtil {
  @NonNls public static final String DESCRIPTIONS = ".descriptions";
  @NonNls public static final String XML_EXTENSION = ".xml";
  static final Logger LOG = Logger.getInstance(InspectionsResultUtil.class);

  @NonNls public static final String PROFILE = "profile";
  @NonNls public static final String INSPECTIONS_NODE = "inspections";
  private static final String ROOT = "root";
  public static final String AGGREGATE = "_aggregate";

  public static void describeInspections(@NonNls Path outputPath, @Nullable String name, @NotNull InspectionProfile profile) throws IOException {
    Map<Pair<String, String>, Set<InspectionToolWrapper<?, ?>>> map = new HashMap<>();
    for (InspectionToolWrapper<?, ?> toolWrapper : profile.getInspectionTools(null)) {
      String groupName = toolWrapper.getGroupDisplayName();
      String[] path = toolWrapper.getGroupPath();
      String groupPath = path.length == 0 ? "" : String.join("/", JBIterable.of(path).take(path.length - 1));
      Set<InspectionToolWrapper<?, ?>> groupInspections = map.computeIfAbsent(
        Pair.create(groupName, groupPath), __ -> new HashSet<>());
      groupInspections.add(toolWrapper);
    }

    try (Writer fw = new OutputStreamWriter(Files.newOutputStream(outputPath), StandardCharsets.UTF_8)) {
      @NonNls final PrettyPrintWriter xmlWriter = new PrettyPrintWriter(fw);
      xmlWriter.startNode(INSPECTIONS_NODE);
      if (name != null) {
        xmlWriter.addAttribute(PROFILE, name);
      }
      List<String> inspectionsWithoutDescriptions = new ArrayList<>(1);
      for (Map.Entry<Pair<String, String>, Set<InspectionToolWrapper<?, ?>>> entry : map.entrySet()) {
        xmlWriter.startNode("group");
        String groupName = entry.getKey().getFirst();
        String groupPath = entry.getKey().getSecond();
        xmlWriter.addAttribute("name", groupName);
        xmlWriter.addAttribute("path", groupPath);
        for (InspectionToolWrapper<?, ?> toolWrapper : entry.getValue()) {
          xmlWriter.startNode("inspection");
          final String shortName = toolWrapper.getShortName();
          xmlWriter.addAttribute("shortName", shortName);
          xmlWriter.addAttribute("defaultSeverity", toolWrapper.getDefaultLevel().getSeverity().getName());
          xmlWriter.addAttribute("displayName", toolWrapper.getDisplayName());
          final boolean toolEnabled = profile.isToolEnabled(HighlightDisplayKey.find(shortName));
          xmlWriter.addAttribute("enabled", Boolean.toString(toolEnabled));
          final String description = toolWrapper.loadDescription();
          if (description != null) {
            xmlWriter.setValue(description);
          }
          else {
            inspectionsWithoutDescriptions.add(shortName);
          }
          xmlWriter.endNode();
        }
        xmlWriter.endNode();
      }
      xmlWriter.endNode();

      if (!inspectionsWithoutDescriptions.isEmpty()) {
        LOG.error("Descriptions are missed for tools: " + StringUtil.join(inspectionsWithoutDescriptions, ", "));
      }
    }
  }

  public static @NotNull Path getInspectionResultPath(@NotNull Path outputDir, String name) {
    return outputDir.resolve(name + XML_EXTENSION);
  }

  public static @NotNull Path getInspectionResultFile(@NotNull Path outputDirectory, @NotNull String name) {
    return outputDirectory.resolve(name + XML_EXTENSION);
  }

  public static @NotNull BufferedWriter getWriter(@NotNull Path outputDirectory, @NotNull String name) throws IOException {
    Path file = getInspectionResultFile(outputDirectory, name);
    Files.createDirectories(outputDirectory);
    return Files.newBufferedWriter(file);
  }

  public static void writeInspectionResult(@NotNull Project project, @NotNull String shortName,
                                           @NotNull Collection<? extends InspectionToolWrapper<?, ?>> wrappers,
                                           @NotNull Path outputDirectory,
                                           @NotNull Function<InspectionToolWrapper, InspectionToolResultExporter> f) throws IOException {
    //dummy entry points tool
    if (wrappers.isEmpty()) return;
    try (XmlWriterWrapper reportWriter = new XmlWriterWrapper(project, outputDirectory, shortName,
                                                              GlobalInspectionContextBase.PROBLEMS_TAG_NAME);
         XmlWriterWrapper aggregateWriter = new XmlWriterWrapper(project, outputDirectory, shortName + AGGREGATE, ROOT)) {
      reportWriter.checkOpen();
      for (InspectionToolWrapper<?, ?> wrapper : wrappers) {
        InspectionToolResultExporter presentation = f.apply(wrapper);
        presentation.exportResults(reportWriter::writeElement, presentation::isExcluded, presentation::isExcluded);
        if (presentation instanceof AggregateResultsExporter) {
          ((AggregateResultsExporter)presentation).exportAggregateResults(aggregateWriter::writeElement);
        }
      }
    }
  }

  public static void writeProfileName(@NotNull Path outputDirectory, @Nullable String profileName) throws IOException {
    Element element = new Element(INSPECTIONS_NODE);
    element.setAttribute(PROFILE, Objects.requireNonNull(profileName));
    JDOMUtil.write(element, outputDirectory.resolve(DESCRIPTIONS + XML_EXTENSION));
  }

  private static final class XmlWriterWrapper implements Closeable {
    private final Project myProject;
    private final Path myOutputDirectory;
    private final String myName;
    private final String myRootTagName;

    private Writer myFileWriter;
    private JbXmlOutputter myOutputter;

    XmlWriterWrapper(@NotNull Project project,
                     @NotNull Path outputDirectory,
                     @NotNull String name,
                     @NotNull String rootTagName) {
      myProject = project;
      myOutputDirectory = outputDirectory;
      myName = name;
      myRootTagName = rootTagName;
    }

    void writeElement(@NotNull Element element) {
      try {
        checkOpen();
        myFileWriter.write('\n');
        myOutputter.output(element, myFileWriter);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    void checkOpen() throws IOException {
      if (myFileWriter == null) {
        myFileWriter = openFile(myOutputDirectory, myName);
        myOutputter = JbXmlOutputter.createOutputter(myProject);
        startWritingXml();
      }
    }

    @Override
    public void close() throws IOException {
      if (myFileWriter == null) {
        return;
      }

      try {
        endWritingXml();
      }
      finally {
        Writer fileWriter = myFileWriter;
        myFileWriter = null;
        fileWriter.close();
      }
    }

    private static @NotNull Writer openFile(@NotNull Path outputDirectory, @NotNull String name) throws IOException {
      return getWriter(outputDirectory, name);
    }

    private void startWritingXml() throws IOException {
      myFileWriter.write('<');
      myFileWriter.write(myRootTagName);
      myFileWriter.write('>');
    }

    private void endWritingXml() throws IOException {
      try {
        myFileWriter.write("\n");
        myFileWriter.write('<');
        myFileWriter.write('/');
        myFileWriter.write(myRootTagName);
        myFileWriter.write('>');
      }
      finally {
        myFileWriter.close();
      }
    }
  }
}
