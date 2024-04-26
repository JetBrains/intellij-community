// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.ui.AggregateResultsExporter;
import com.intellij.configurationStore.JbXmlOutputter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.JBIterable;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

public final class InspectionsResultUtil {
  public static final @NonNls String DESCRIPTIONS = ".descriptions";
  public static final @NonNls String XML_EXTENSION = ".xml";
  private static final Logger LOG = Logger.getInstance(InspectionsResultUtil.class);

  public static final @NonNls String PROFILE = "profile";
  public static final @NonNls String INSPECTIONS_NODE = "inspections";
  private static final String ROOT = "root";
  public static final String AGGREGATE = "_aggregate";

  public static void describeInspections(@NonNls Path outputPath, @Nullable String name, @NotNull InspectionProfile profile)
    throws IOException, XMLStreamException {
    Map<Pair<String, String>, Set<InspectionToolWrapper<?, ?>>> map = new HashMap<>();
    for (InspectionToolWrapper<?, ?> toolWrapper : profile.getInspectionTools(null)) {
      String groupName = toolWrapper.getGroupDisplayName();
      String[] path = toolWrapper.getGroupPath();
      String groupPath = path.length == 0 ? "" : String.join("/", JBIterable.of(path).take(path.length - 1));
      Set<InspectionToolWrapper<?, ?>> groupInspections = map.computeIfAbsent(
        new Pair<>(groupName, groupPath), __ -> new HashSet<>());
      groupInspections.add(toolWrapper);
    }

    try (Writer fw = new OutputStreamWriter(Files.newOutputStream(outputPath), StandardCharsets.UTF_8)) {
      XMLStreamWriter xmlWriter = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(fw);
      xmlWriter.writeStartElement(INSPECTIONS_NODE);
      if (name != null) {
        xmlWriter.writeAttribute(PROFILE, name);
      }
      List<String> inspectionsWithoutDescriptions = new ArrayList<>(1);
      for (Map.Entry<Pair<String, String>, Set<InspectionToolWrapper<?, ?>>> entry : map.entrySet()) {
        xmlWriter.writeStartElement("group");
        String groupName = entry.getKey().getFirst();
        String groupPath = entry.getKey().getSecond();
        xmlWriter.writeAttribute("name", groupName);
        xmlWriter.writeAttribute("path", groupPath);
        for (InspectionToolWrapper<?, ?> toolWrapper : entry.getValue()) {
          xmlWriter.writeStartElement("inspection");
          final String shortName = toolWrapper.getShortName();
          xmlWriter.writeAttribute("shortName", shortName);
          xmlWriter.writeAttribute("defaultSeverity", toolWrapper.getDefaultLevel().getSeverity().getName());
          xmlWriter.writeAttribute("displayName", toolWrapper.getDisplayName());
          xmlWriter.writeAttribute("enabled", Boolean.toString(isToolEnabled(profile, shortName)));
          String language = toolWrapper.getLanguage();
          if (language != null) {
            xmlWriter.writeAttribute("language", language);
          }
          InspectionEP extension = toolWrapper.getExtension();
          if (extension != null) {
            PluginDescriptor plugin = extension.getPluginDescriptor();
            String pluginId = plugin.getPluginId().getIdString();
            xmlWriter.writeAttribute("pluginId", pluginId);
            xmlWriter.writeAttribute("pluginVersion", plugin.getVersion());
          }
          xmlWriter.writeAttribute("isGlobalTool", String.valueOf(toolWrapper instanceof GlobalInspectionToolWrapper));

          final String description = toolWrapper.loadDescription();
          if (description != null) {
            xmlWriter.writeCharacters(description);
          }
          else {
            inspectionsWithoutDescriptions.add(shortName);
          }
          xmlWriter.writeEndElement();
        }
        xmlWriter.writeEndElement();
      }
      xmlWriter.writeEndElement();

      if (!inspectionsWithoutDescriptions.isEmpty()) {
        LOG.error("Descriptions are missed for tools: " + StringUtil.join(inspectionsWithoutDescriptions, ", "));
      }
    }
  }

  private static boolean isToolEnabled(@NotNull InspectionProfile profile, @NotNull String shortName) {
    if (profile instanceof InspectionProfileImpl) {
      ToolsImpl tools = ((InspectionProfileImpl)profile).getToolsOrNull(shortName, null);
      if (tools != null)  {
        return tools.isEnabled();
      }
    }
    return profile.isToolEnabled(HighlightDisplayKey.find(shortName));
  }

  public static @NotNull Path getInspectionResultPath(@NotNull Path outputDir, @NotNull String shortName) {
    return outputDir.resolve(shortName + XML_EXTENSION);
  }

  public static @NotNull BufferedWriter getWriter(@NotNull Path outputDirectory, @NotNull String name) throws IOException {
    Path file = getInspectionResultPath(outputDirectory, name);
    Files.createDirectories(outputDirectory);
    return Files.newBufferedWriter(file);
  }

  public static void writeInspectionResult(@NotNull Project project, @NotNull String shortName,
                                           @NotNull Collection<? extends InspectionToolWrapper<?, ?>> wrappers,
                                           @NotNull Path outputDirectory,
                                           @NotNull Function<? super InspectionToolWrapper<?, ?>, ? extends InspectionToolResultExporter> presentationGetter) throws IOException {
    //dummy entry points tool
    if (wrappers.isEmpty()) return;
    try (XmlWriterWrapper reportWriter = new XmlWriterWrapper(project, outputDirectory, shortName, GlobalInspectionContextBase.PROBLEMS_TAG_NAME);
         XmlWriterWrapper aggregateWriter = new XmlWriterWrapper(project, outputDirectory, shortName + AGGREGATE, ROOT)) {
      reportWriter.checkOpen();
      for (InspectionToolWrapper<?, ?> wrapper : wrappers) {
        InspectionToolResultExporter presentation = presentationGetter.apply(wrapper);
        presentation.exportResults(reportWriter::writeElement, presentation::isExcluded, presentation::isExcluded);
        if (presentation instanceof AggregateResultsExporter) {
          ((AggregateResultsExporter)presentation).exportAggregateResults(aggregateWriter::writeElement);
        }
      }
    }
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
        myOutputter = JbXmlOutputter.Companion.createOutputter(myProject);
        startWritingXml();
      }
    }

    @Override
    public void close() throws IOException {
      if (myFileWriter == null) {
        return;
      }

      Writer writer = myFileWriter;
      try (writer) {
        endWritingXml();
      }
      finally {
        myFileWriter = null;
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
