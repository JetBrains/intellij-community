// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import com.intellij.codeInspection.DefaultInspectionToolResultExporter;
import com.intellij.codeInspection.InspectionsReportConverter;
import com.intellij.codeInspection.InspectionsResultUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
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
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

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
  @NonNls private static final String SEVERITY_ATTR = "severity";
  @NonNls private static final String ATTRIBUTE_KEY_ATTR = "attribute_key";
  @NonNls private static final String HINT = "hint";
  @NonNls private static final String HINTS = "hints";
  @NonNls private static final String DISPLAY_NAME = "displayName";
  @NonNls private static final String SHORT_NAME = "shortName";
  @NonNls private static final String ENABLED = "enabled";
  @NonNls private static final String NAME = "name";
  @NonNls private static final String ID = "id";
  @NonNls private static final String VALUE = "value";
  @NonNls private static final String GROUP = "group";
  @NonNls private static final String GROUPS = "groups";
  @NonNls private static final String INSPECTION = "inspection";
  @NonNls private static final String HIGHLIGHTED_ELEMENT = "highlighted_element";
  @NonNls private static final String PROJECT_FINGERPRINT = "ProjectFingerprint";
  @NonNls private static final String FILE_FINGERPRINT = "file_fingerprint";
  @NonNls private static final String FILE_NAME = "file_name";
  @NonNls private static final String FILE_PATH = "file_path";
  @NonNls private static final String LANGUAGE = "language";
  @NonNls private static final String LINES_COUNT = "lines_count";
  @NonNls private static final String MODIFICATION_TIMESTAMP = "modification_timestamp";
  @NonNls private static final String DUPLICATED_CODE_AGGREGATE = "DuplicatedCode" + InspectionsResultUtil.AGGREGATE;

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
      try (Writer writer = Files.newBufferedWriter(jsonFile.toPath(), CharsetToolkit.UTF8_CHARSET);
           JsonWriter jsonWriter = gson.newJsonWriter(writer)) {
        Document doc = builder.build(inspectionDataFile);
        if (InspectionsResultUtil.DESCRIPTIONS.equals(fileNameWithoutExt)) {
          convertDescriptions(jsonWriter, doc);
        }
        else if (PROJECT_FINGERPRINT.equals(fileNameWithoutExt)) {
          convertProjectFingerprint(jsonWriter, doc);
        }
        else if (DUPLICATED_CODE_AGGREGATE.equals(fileNameWithoutExt)) {
          convertDuplicatedCode(jsonWriter, doc);
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
      jsonWriter.beginArray();
      for (Element fragment : duplicates.getChildren("fragment")) {
        convertDuplicateFragment(jsonWriter, fragment);
      }
      jsonWriter.endArray();
    }
    jsonWriter.endArray();
    jsonWriter.endObject();
  }

  private static void convertDuplicateFragment(@NotNull JsonWriter jsonWriter, Element fragment) throws IOException {
    jsonWriter.beginObject();
    jsonWriter.name(FILE).value(fragment.getAttributeValue(FILE));
    String line = fragment.getAttributeValue(LINE);
    String start = fragment.getAttributeValue("start");
    String end = fragment.getAttributeValue("end");
    assert line != null;
    assert start != null;
    assert end != null;
    jsonWriter.name(LINE).value(Integer.parseInt(line));
    jsonWriter.name("start").value(Integer.parseInt(start));
    jsonWriter.name("end").value(Integer.parseInt(end));
    jsonWriter.endObject();
  }

  private static void convertProjectFingerprint(@NotNull JsonWriter jsonWriter, @NotNull Document problems) throws IOException {
    jsonWriter.beginObject();
    jsonWriter.name(PROBLEMS);
    jsonWriter.beginArray();
    for (Element fileFingerprint : problems.getRootElement().getChildren(FILE_FINGERPRINT)) {
      convertFileFingerprint(jsonWriter, fileFingerprint);
    }
    jsonWriter.endArray();
    jsonWriter.endObject();
  }

  private static void convertFileFingerprint(@NotNull JsonWriter writer, @NotNull Element problem) throws IOException {
    writer.beginObject();
    writer.name(FILE_NAME).value(problem.getChildText(FILE_NAME));
    writer.name(FILE_PATH).value(problem.getChildText(FILE_PATH));
    writer.name(LANGUAGE).value(problem.getChildText(LANGUAGE));
    try {
      int linesCount = Integer.parseInt(problem.getChildText(LINES_COUNT));
      writer.name(LINES_COUNT).value(linesCount);
    }
    catch (NumberFormatException e) {
      writer.name(LINES_COUNT).nullValue();
    }
    try {
      long modificationStamp = Long.parseLong(problem.getChildText(MODIFICATION_TIMESTAMP));
      writer.name(MODIFICATION_TIMESTAMP).value(modificationStamp);
    }
    catch (NumberFormatException e) {
      writer.name(MODIFICATION_TIMESTAMP).nullValue();
    }
    writer.endObject();
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

  protected static void convertProblem(@NotNull JsonWriter writer, @NotNull Element problem) throws IOException {
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
                                                    @Nullable Predicate<String> inspectionFilter) throws IOException {
    Element inspectionsElement = descriptions.getRootElement();
    writer.name(InspectionsResultUtil.PROFILE).value(inspectionsElement.getAttributeValue(InspectionsResultUtil.PROFILE));
    writer.name(GROUPS);
    writer.beginArray();
    for (Element group : inspectionsElement.getChildren(GROUP)) {
      convertGroup(writer, group, inspectionFilter);
    }
    writer.endArray();
  }

  private static void convertGroup(@NotNull JsonWriter writer, @NotNull Element group, @Nullable Predicate<String> inspectionFilter) throws IOException {
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
      .name(ENABLED).value(Boolean.parseBoolean(inspection.getAttributeValue(ENABLED)))
      .name(DESCRIPTION).value(inspection.getValue())
      .endObject();
  }
}
