// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import com.intellij.codeInspection.DefaultInspectionToolResultExporter;
import com.intellij.codeInspection.InspectionsReportConverter;
import com.intellij.codeInspection.InspectionsResultUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.intellij.codeInspection.DefaultInspectionToolResultExporter.INSPECTION_RESULTS_LANGUAGE;
import static com.intellij.codeInspection.reference.SmartRefElementPointerImpl.*;

public class JsonInspectionsReportConverter implements InspectionsReportConverter {
  @NonNls private static final String FORMAT_NAME = "json";
  @NonNls private static final String JSON_EXTENSION = ".json";
  @NonNls private static final String FILE = "file";
  @NonNls private static final String LINE = "line";
  @NonNls private static final String OFFSET = "offset";
  @NonNls private static final String LENGTH = "length";
  @NonNls private static final String MODULE = "module";
  @NonNls private static final String PACKAGE = "package";
  @NonNls protected static final String PROBLEM = "problem";
  @NonNls protected static final String PROBLEMS = "problems";
  @NonNls private static final String DESCRIPTION = "description";
  @NonNls private static final String PLUGIN_ID = "pluginId";
  @NonNls private static final String PLUGIN_VERSION = "pluginVersion";
  @NonNls private static final String GLOBAL_TOOL = "isGlobalTool";
  @NonNls private static final String LANGUAGE = "language";
  @NonNls private static final String SEVERITY_ATTR = "severity";
  @NonNls private static final String ATTRIBUTE_KEY_ATTR = "attribute_key";
  @NonNls private static final String HINT = "hint";
  @NonNls private static final String HINTS = "hints";
  @NonNls private static final String DISPLAY_NAME = "displayName";
  @NonNls private static final String DEFAULT_SEVERITY = "defaultSeverity";
  @NonNls private static final String SHORT_NAME = "shortName";
  @NonNls private static final String ENABLED = "enabled";
  @NonNls private static final String NAME = "name";
  @NonNls private static final String ID = "id";
  @NonNls private static final String VALUE = "value";
  @NonNls private static final String GROUP = "group";
  @NonNls private static final String GROUPS = "groups";
  @NonNls private static final String INSPECTION = "inspection";
  @NonNls private static final String HIGHLIGHTED_ELEMENT = "highlighted_element";
  @NonNls public static final String DUPLICATED_CODE = "DuplicatedCode";
  @NonNls public static final String DUPLICATED_CODE_AGGREGATE = DUPLICATED_CODE + InspectionsResultUtil.AGGREGATE;
  @NonNls public static final String PHP_VULNERABLE_PATHS_AGGREGATE = "PhpVulnerablePathsInspection" + InspectionsResultUtil.AGGREGATE;

  @Override
  public String getFormatName() {
    return FORMAT_NAME;
  }

  @Override
  public boolean useTmpDirForRawData() {
    return true;
  }

  @Override
  public void convert(@NotNull String rawDataDirectoryPath,
                      @Nullable String outputPath,
                      @NotNull Map<String, Tools> tools,
                      @NotNull List<? extends File> inspectionsResults) throws ConversionException {
    if (outputPath == null) {
      throw new ConversionException("Output path isn't specified");
    }
    try {
      Files.createDirectories(new File(outputPath).toPath());
    }
    catch (IOException e) {
      throw new ConversionException("Cannot create dirs in output path: " + outputPath + " error: " + e.getMessage());
    }
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    SAXBuilder builder = new SAXBuilder();
    for (File inspectionDataFile : inspectionsResults) {
      String fileNameWithoutExt = FileUtil.getNameWithoutExtension(inspectionDataFile);
      File jsonFile = new File(outputPath, fileNameWithoutExt + JSON_EXTENSION);
      try (Writer writer = Files.newBufferedWriter(jsonFile.toPath(), StandardCharsets.UTF_8);
           JsonWriter jsonWriter = gson.newJsonWriter(writer)) {
        Document doc = builder.build(inspectionDataFile);
        if (InspectionsResultUtil.DESCRIPTIONS.equals(fileNameWithoutExt)) {
          convertDescriptions(jsonWriter, doc);
        }
        else if (DUPLICATED_CODE_AGGREGATE.equals(fileNameWithoutExt)) {
          convertDuplicatedCode(jsonWriter, doc);
        }
        else if (PHP_VULNERABLE_PATHS_AGGREGATE.equals(fileNameWithoutExt)) {
          convertPhpVulnerablePaths(jsonWriter, doc);
        }
        else {
          convertProblems(jsonWriter, doc);
        }
      }
      catch (IOException | JDOMException e) {
        throw new ConversionException("Cannot convert file: " + inspectionDataFile.getPath() + " error: " + e.getMessage());
      }
    }
  }

  private static void convertDuplicatedCode(@NotNull JsonWriter jsonWriter, @NotNull Document problems) throws IOException {
    jsonWriter.beginObject();
    jsonWriter.name(PROBLEMS);
    jsonWriter.beginArray();
    for (Element duplicates : problems.getRootElement().getChildren("duplicate")) {
      convertDuplicates(jsonWriter, duplicates);
    }
    jsonWriter.endArray();
    jsonWriter.endObject();
  }

  public static void convertDuplicates(@NotNull JsonWriter jsonWriter, Element duplicates) throws IOException {
    jsonWriter.beginArray();
    for (Element fragment : duplicates.getChildren("fragment")) {
      convertDuplicateFragment(jsonWriter, fragment);
    }
    jsonWriter.endArray();
  }

  private static void convertDuplicateFragment(@NotNull JsonWriter jsonWriter, Element fragment) throws IOException {
    jsonWriter.beginObject();
    writeFileSegmentAttributes(jsonWriter, fragment);
    jsonWriter.endObject();
  }

  private static void writeFileSegmentAttributes(@NotNull JsonWriter jsonWriter, Element fileSegment) throws IOException {
    writeFileAttribute(jsonWriter, fileSegment);
    writeSegmentAttributes(jsonWriter, fileSegment);
  }

  private static void writeFileAttribute(@NotNull JsonWriter jsonWriter, Element fileSegment) throws IOException {
    jsonWriter.name(FILE).value(fileSegment.getAttributeValue(FILE));
  }

  private static void writeSegmentAttributes(@NotNull JsonWriter jsonWriter, Element segment) throws IOException {
    String line = segment.getAttributeValue(LINE);
    String start = segment.getAttributeValue("start");
    String end = segment.getAttributeValue("end");
    assert line != null;
    assert start != null;
    assert end != null;
    jsonWriter.name(LINE).value(Integer.parseInt(line));
    jsonWriter.name("start").value(Integer.parseInt(start));
    jsonWriter.name("end").value(Integer.parseInt(end));
  }

  public static void convertPhpVulnerablePaths(@NotNull JsonWriter jsonWriter, @NotNull Document problems) throws IOException {
    jsonWriter.beginObject();
    jsonWriter.name(PROBLEMS);
    jsonWriter.beginArray();
    for (Element problem : problems.getRootElement().getChildren(PROBLEM)) {
      convertPhpVulnerablePath(jsonWriter, problem);
    }
    jsonWriter.endArray();
    jsonWriter.endObject();
  }

  public static void convertPhpVulnerablePath(@NotNull JsonWriter jsonWriter, Element problem) throws IOException {
    jsonWriter.beginObject();

    jsonWriter.name(DESCRIPTION).value("Vulnerable code flow");
    jsonWriter.name("fragments");
    jsonWriter.beginArray();
    Element fragmentsElement = problem.getChild("fragments");
    assert fragmentsElement != null;
    for (Element fragment : fragmentsElement.getChildren("fragment")) {
      convertPhpVulnerableFragment(jsonWriter, fragment);
    }
    jsonWriter.endArray();

    convertPhpSink(jsonWriter, problem);

    jsonWriter.name("sources");
    jsonWriter.beginArray();
    Element sourcesElement = problem.getChild("sources");
    assert sourcesElement != null;
    for (Element source : sourcesElement.getChildren("source")) {
      convertPhpTaintSource(jsonWriter, source);
    }
    jsonWriter.endArray();

    jsonWriter.name(LANGUAGE).value(problem.getChildText(LANGUAGE));
    Element problemClassElement = problem.getChild(DefaultInspectionToolResultExporter.INSPECTION_RESULTS_PROBLEM_CLASS_ELEMENT);
    if (problemClassElement != null) {
      convertProblemClass(jsonWriter, problemClassElement);
    }

    jsonWriter.endObject();
  }

  private static void convertPhpSink(@NotNull JsonWriter jsonWriter, @NotNull Element problem) throws IOException {
    jsonWriter.name("sink");
    Element sink = problem.getChild("sink");
    assert sink != null;
    jsonWriter.beginObject();
    jsonWriter.name("text").value(sink.getAttributeValue("text"));
    String sinkFqn = sink.getAttributeValue("fqn");
    if (sinkFqn != null) {
      jsonWriter.name("fqn").value(sinkFqn);
    }
    writeOrderAttribute(jsonWriter, sink);
    jsonWriter.name("vulnerabilities");
    jsonWriter.beginArray();
    Collection<String> vulnerabilityValues = getVulnerabilityValues(sink, "vulnerabilities");
    for (String vulnerability : vulnerabilityValues) {
      jsonWriter.value(vulnerability);
    }
    jsonWriter.endArray();
    jsonWriter.name("parameters");
    jsonWriter.beginArray();
    String parameterName = getParameterName(sink);
    if (parameterName != null) {
      jsonWriter.value(parameterName);
    }
    jsonWriter.endArray();
    jsonWriter.endObject();
  }

  private static void convertPhpTaintSource(@NotNull JsonWriter jsonWriter, @NotNull Element source) throws IOException {
    jsonWriter.beginObject();
    jsonWriter.name("text").value(source.getAttributeValue("text"));
    writeFileAttribute(jsonWriter, source);
    writeOrderAttribute(jsonWriter, source);
    writeSanitizedVulnerabilities(jsonWriter, source);
    jsonWriter.endObject();
  }

  private static void convertPhpVulnerableFragment(@NotNull JsonWriter jsonWriter, Element fragment) throws IOException {
    jsonWriter.beginObject();

    writeFileSegmentAttributes(jsonWriter, fragment);

    jsonWriter.name("markers");
    jsonWriter.beginArray();
    for (Element marker : fragment.getChildren("marker")) {
      convertPhpTaintMarker(jsonWriter, marker);
    }
    jsonWriter.endArray();

    jsonWriter.endObject();
  }

  private static void convertPhpTaintMarker(@NotNull JsonWriter jsonWriter, @NotNull Element marker) throws IOException {
    jsonWriter.beginObject();

    writeSegmentAttributes(jsonWriter, marker);
    writeOrderAttribute(jsonWriter, marker);

    jsonWriter.name("successors");
    jsonWriter.beginArray();
    Element successorsElement = marker.getChild("successors");
    if (successorsElement != null) {
      Collection<String> markerOrders = ContainerUtil.map(successorsElement.getChildren("marker"), Element::getText);
      for (String markerOrder : markerOrders) {
        jsonWriter.value(markerOrder);
      }
    }
    jsonWriter.endArray();
    jsonWriter.name("predecessors");
    jsonWriter.beginArray();
    Element predecessorsElement = marker.getChild("predecessors");
    if (predecessorsElement != null) {
      Collection<String> markerOrders = ContainerUtil.map(predecessorsElement.getChildren("marker"), Element::getText);
      for (String markerOrder : markerOrders) {
        jsonWriter.value(markerOrder);
      }
    }
    jsonWriter.endArray();

    writeSanitizedVulnerabilities(jsonWriter, marker);

    jsonWriter.endObject();
  }

  private static void writeOrderAttribute(@NotNull JsonWriter jsonWriter, @NotNull Element marker) throws IOException {
    jsonWriter.name("order").value(marker.getAttributeValue("order"));
  }

  private static void writeSanitizedVulnerabilities(@NotNull JsonWriter jsonWriter, @NotNull Element element) throws IOException {
    jsonWriter.name("sanitized_vulnerabilities");
    jsonWriter.beginArray();
    Collection<String> vulnerabilityValues = getVulnerabilityValues(element, "sanitized_vulnerabilities");
    for (String vulnerability : vulnerabilityValues) {
      jsonWriter.value(vulnerability);
    }
    jsonWriter.endArray();
  }

  @Nullable
  private static String getParameterName(@NotNull Element element) {
    Element parameters = element.getChild("parameters");
    Collection<? extends Element> parameterElements = parameters != null ? parameters.getChildren("parameter") : null;
    Element parameter = ContainerUtil.getFirstItem(parameterElements);
    return parameter != null ? parameter.getAttributeValue("name") : null;
  }

  @NotNull
  private static Collection<String> getVulnerabilityValues(@NotNull Element element,
                                                           @NotNull String vulnerabilitiesTagName) {
    Element vulnerabilities = element.getChild(vulnerabilitiesTagName);
    if (vulnerabilities == null) {
      return Collections.emptyList();
    }
    return ContainerUtil.map(vulnerabilities.getChildren("vulnerability"),
                             vulnerability -> vulnerability.getAttributeValue("name"));
  }

  private static void convertProblems(@NotNull JsonWriter jsonWriter, @NotNull Document problems) throws IOException {
    jsonWriter.beginObject();
    jsonWriter.name(PROBLEMS);
    jsonWriter.beginArray();
    for (Element problem : problems.getRootElement().getChildren(PROBLEM)) {
      convertProblem(jsonWriter, problem);
    }
    jsonWriter.endArray();
    jsonWriter.endObject();
  }

  public static void convertProblem(@NotNull JsonWriter writer, @NotNull Element problem) throws IOException {
    writer.beginObject();
    writer.name(FILE).value(problem.getChildText(FILE));
    writeInt(writer, problem, LINE);
    writeInt(writer, problem, OFFSET);
    writeInt(writer, problem, LENGTH);
    writer.name(MODULE).value(problem.getChildText(MODULE));
    writer.name(PACKAGE).value(problem.getChildText(PACKAGE));

    Element problemClassElement = problem.getChild(DefaultInspectionToolResultExporter.INSPECTION_RESULTS_PROBLEM_CLASS_ELEMENT);
    if (problemClassElement != null) {
      convertProblemClass(writer, problemClassElement);
    }

    Element entryPoint = problem.getChild(ENTRY_POINT);
    if (entryPoint != null) {
      convertEntryPoint(writer, entryPoint);
    }

    Element hints = problem.getChild(HINTS);
    if (hints != null) {
      convertHints(writer, hints);
    }

    writer.name(HIGHLIGHTED_ELEMENT).value(problem.getChildText(HIGHLIGHTED_ELEMENT));
    writer.name(INSPECTION_RESULTS_LANGUAGE).value(problem.getChildText(INSPECTION_RESULTS_LANGUAGE));
    writer.name(DESCRIPTION).value(problem.getChildText(DESCRIPTION));
    writer.endObject();
  }

  private static void writeInt(@NotNull JsonWriter writer, @NotNull Element problem, @NotNull String elementName) throws IOException {
    try {
      int intValue = Integer.parseInt(problem.getChildText(elementName));
      writer.name(elementName).value(intValue);
    }
    catch (NumberFormatException e) {
      writer.name(elementName).nullValue();
    }
  }

  private static void convertProblemClass(@NotNull JsonWriter writer, @NotNull Element problemClass) throws IOException {
    writer.name(DefaultInspectionToolResultExporter.INSPECTION_RESULTS_PROBLEM_CLASS_ELEMENT);
    writer.beginObject()
      .name(NAME).value(problemClass.getText());

    String inspectionId = problemClass.getAttributeValue(DefaultInspectionToolResultExporter.INSPECTION_RESULTS_ID_ATTRIBUTE);
    if (inspectionId != null) {
      writer.name(ID).value(inspectionId);
    }

    writer
      .name(SEVERITY_ATTR).value(problemClass.getAttributeValue(SEVERITY_ATTR))
      .name(ATTRIBUTE_KEY_ATTR).value(problemClass.getAttributeValue(ATTRIBUTE_KEY_ATTR))
      .endObject();
  }

  private static void convertEntryPoint(@NotNull JsonWriter writer, @NotNull Element entryPoint) throws IOException {
    writer.name(ENTRY_POINT);
    writer.beginObject()
      .name(TYPE_ATTR).value(entryPoint.getAttributeValue(TYPE_ATTR))
      .name(FQNAME_ATTR).value(entryPoint.getAttributeValue(FQNAME_ATTR))
      .endObject();
  }

  private static void convertHints(@NotNull JsonWriter writer, @NotNull Element hints) throws IOException {
    writer.name(HINTS);
    writer.beginArray();
    for (Element hint : hints.getChildren(HINT)) {
      writer.value(hint.getAttributeValue(VALUE));
    }
    writer.endArray();
  }

  private static void convertDescriptions(@NotNull JsonWriter writer, @NotNull Document descriptions) throws IOException {
    writer.beginObject();
    convertDescriptionsContents(writer, descriptions, null);
    writer.endObject();
  }

  protected static void convertDescriptionsContents(@NotNull JsonWriter writer,
                                                    @NotNull Document descriptions,
                                                    @Nullable Predicate<? super String> inspectionFilter) throws IOException {
    Element inspectionsElement = descriptions.getRootElement();
    writer.name(InspectionsResultUtil.PROFILE).value(inspectionsElement.getAttributeValue(InspectionsResultUtil.PROFILE));
    writer.name(GROUPS);
    writer.beginArray();
    for (Element group : inspectionsElement.getChildren(GROUP)) {
      convertGroup(writer, group, inspectionFilter);
    }
    writer.endArray();
  }

  private static void convertGroup(@NotNull JsonWriter writer, @NotNull Element group, @Nullable Predicate<? super String> inspectionFilter) throws IOException {
    if (inspectionFilter != null) {
      boolean anyInspectionsInFilter = false;
      for (Element inspection : group.getChildren(INSPECTION)) {
        if (inspectionFilter.test(inspection.getAttributeValue(SHORT_NAME))) {
          anyInspectionsInFilter = true;
          break;
        }
      }
      if (!anyInspectionsInFilter) return;
    }
    writer.beginObject();
    writer.name(NAME).value(group.getAttributeValue(NAME));
    writer.name(InspectionsResultUtil.INSPECTIONS_NODE).beginArray();
    for (Element inspection : group.getChildren(INSPECTION)) {
      if (inspectionFilter != null && !inspectionFilter.test(inspection.getAttributeValue(SHORT_NAME))) continue;
      convertInspectionDescription(writer, inspection);
    }
    writer.endArray();
    writer.endObject();
  }

  private static void convertInspectionDescription(@NotNull JsonWriter writer, @NotNull Element inspection) throws IOException {
    writer.beginObject()
      .name(SHORT_NAME).value(inspection.getAttributeValue(SHORT_NAME))
      .name(DISPLAY_NAME).value(inspection.getAttributeValue(DISPLAY_NAME))
      .name(DEFAULT_SEVERITY).value(inspection.getAttributeValue(DEFAULT_SEVERITY))
      .name(PLUGIN_ID).value(inspection.getAttributeValue(PLUGIN_ID))
      .name(PLUGIN_VERSION).value(inspection.getAttributeValue(PLUGIN_VERSION))
      .name(LANGUAGE).value(inspection.getAttributeValue(LANGUAGE))
      .name(GLOBAL_TOOL).value(Boolean.parseBoolean(inspection.getAttributeValue(GLOBAL_TOOL)))
      .name(ENABLED).value(Boolean.parseBoolean(inspection.getAttributeValue(ENABLED)))
      .name(DESCRIPTION).value(inspection.getValue())
      .endObject();
  }
}
