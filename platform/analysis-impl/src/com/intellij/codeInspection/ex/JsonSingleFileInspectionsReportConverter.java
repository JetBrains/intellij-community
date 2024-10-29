// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import com.intellij.codeInspection.InspectionsResultUtil;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
public final class JsonSingleFileInspectionsReportConverter extends JsonInspectionsReportConverter {
  @Override
  public String getFormatName() {
    return "json-single-file";
  }

  @Override
  public void convert(@NotNull String rawDataDirectoryPath,
                      @Nullable String outputPath,
                      @NotNull Map<String, Tools> tools,
                      @NotNull List<? extends File> inspectionsResults) throws ConversionException {
    if (outputPath == null) {
      throw new ConversionException("Output path isn't specified");
    }
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    try {
      try (Writer writer = Files.newBufferedWriter(Paths.get(outputPath), StandardCharsets.UTF_8);
           JsonWriter jsonWriter = gson.newJsonWriter(writer)) {
        jsonWriter.beginObject();

        File patches = ContainerUtil.find(inspectionsResults,
                                          (file) -> FileUtil.getNameWithoutExtension(file).equals("fixes"));
        if (patches != null) {
          Element patchesElement = JDOMUtil.load(patches);
          jsonWriter.name("patch").value(patchesElement.getTextTrim());
          jsonWriter.name("patchedFiles");
          jsonWriter.beginArray();
          for (Element patchedFile : patchesElement.getChildren("patchedFile")) {
            jsonWriter.beginObject();
            jsonWriter.name("path").value(patchedFile.getAttributeValue("path"));
            jsonWriter.name("content").value(patchedFile.getTextTrim());
            jsonWriter.endObject();
          }
          jsonWriter.endArray();
        }

        jsonWriter.name(PROBLEMS);
        jsonWriter.beginArray();

        Set<String> seenProblemIds = new HashSet<>();

        for (File result : inspectionsResults) {
          if (!FileUtil.getNameWithoutExtension(result).equals(InspectionsResultUtil.DESCRIPTIONS)) {
            seenProblemIds.add(FileUtil.getNameWithoutExtension(result));
            var element = JDOMUtil.load(result);
            for (Element problem : element.getChildren(PROBLEM)) {
              convertProblem(jsonWriter, problem);
            }
          }
        }
        jsonWriter.endArray();

        File descriptionsFile = ContainerUtil.find(inspectionsResults,
                                                   (file) -> FileUtil.getNameWithoutExtension(file).equals(InspectionsResultUtil.DESCRIPTIONS));
        if (descriptionsFile != null) {
          convertDescriptionsContents(jsonWriter, JDOMUtil.load(descriptionsFile), (id) -> seenProblemIds.contains(id));
        }

        jsonWriter.endObject();
      }
    }
    catch (IOException | JDOMException e) {
      throw new ConversionException("Cannot convert file: " + e.getMessage());
    }
  }
}
