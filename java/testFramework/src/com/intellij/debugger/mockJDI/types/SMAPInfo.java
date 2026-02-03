// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.debugger.mockJDI.types;

import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SMAPInfo {
  private static final @NonNls String SMAP_ID = "SMAP";
  private static final @NonNls String STRATUM_SECTION_PREFIX = "*S ";
  private final BufferedReader myReader;
  private String myOutputFileName;
  private String myDefaultStratum;
  private final Map<String, StratumInfo> myId2Stratum = new HashMap<>();
  private static final @NonNls String END_SECTION_HEADER = "*E";
  private static final @NonNls String FILE_SECTION_HEADER = "*F";
  private static final @NonNls String LINE_SECTION_HEADER = "*L";
  private static final @NonNls String OPEN_EMBEDDED_STRATUM_HEADER = "*O";
  private static final @NonNls String CLOSE_EMBEDDED_STRATUM_HEADER = "*C";
  private String myLastFileID;

  public static @Nullable SMAPInfo parse(final Reader SMAPReader) {
    final SMAPInfo smapInfo = new SMAPInfo(SMAPReader);
    try {
      smapInfo.doParse();
    }
    catch (Exception e) {
      return null;
    }
    if (smapInfo.myId2Stratum.isEmpty()) {
      return null;
    }
    return smapInfo;
  }

  private SMAPInfo(final Reader SMAPReader) {
    myReader = new BufferedReader(SMAPReader);
  }

  private void doParse() throws IOException {
    final String s = myReader.readLine();
    if (!SMAP_ID.equals(s)) {
      return;
    }

    myOutputFileName = myReader.readLine();
    myDefaultStratum = myReader.readLine();
    String sectionHeader = myReader.readLine();
    StratumInfo currentStratum = null;
    int level = 0;
    while (sectionHeader != null && !END_SECTION_HEADER.equals(sectionHeader)) {
      if (sectionHeader.startsWith(OPEN_EMBEDDED_STRATUM_HEADER)) {
        ++level;
      }
      else if (sectionHeader.startsWith(CLOSE_EMBEDDED_STRATUM_HEADER)) {
        --level;
      }
      else if (level == 0) {

        if (sectionHeader.startsWith(STRATUM_SECTION_PREFIX)) {
          String stratumId = sectionHeader.substring(STRATUM_SECTION_PREFIX.length());
          currentStratum = new StratumInfo(stratumId);
          myId2Stratum.put(stratumId, currentStratum);
        }
        else if (sectionHeader.equals(FILE_SECTION_HEADER)) {
          String fileInfo = myReader.readLine();
          while (fileInfo != null && !fileInfo.startsWith("*")) {
            parseFileInfo(currentStratum, fileInfo);
            fileInfo = myReader.readLine();
          }
          sectionHeader = fileInfo;
          continue;
        }
        else if (sectionHeader.equals(LINE_SECTION_HEADER)) {
          myLastFileID = "0";
          String lineInfo = myReader.readLine();
          while (lineInfo != null && !lineInfo.startsWith("*")) {
            parseLineInfo(currentStratum, lineInfo);
            lineInfo = myReader.readLine();
          }
          sectionHeader = lineInfo;
          continue;
        }
      }


      sectionHeader = myReader.readLine();
    }
  }

  private void parseLineInfo(final StratumInfo currentStratum, final String lineInfo) {
    final int colonIndex = lineInfo.indexOf(':');
    String inputInfo = lineInfo.substring(0, colonIndex);
    String outputInfo = lineInfo.substring(colonIndex + 1);

    int repeatCount;
    final int inputCommaIndex = inputInfo.indexOf(',');
    if (inputCommaIndex != -1) {
      repeatCount = Integer.parseInt(inputInfo.substring(inputCommaIndex + 1));
      inputInfo = inputInfo.substring(0, inputCommaIndex);
    }
    else {
      repeatCount = 1;
    }

    int sharpIndex = inputInfo.indexOf('#');
    final String fileId;
    if (sharpIndex != -1) {
      fileId = inputInfo.substring(sharpIndex + 1);
      inputInfo = inputInfo.substring(0, sharpIndex);
    }
    else {
      fileId = myLastFileID;
    }
    int inputStartLine = Integer.parseInt(inputInfo);

    final int outputCommaIndex = outputInfo.indexOf(',');
    final int increment;
    if (outputCommaIndex != -1) {
      increment = Integer.parseInt(outputInfo.substring(outputCommaIndex + 1));
      outputInfo = outputInfo.substring(0, outputCommaIndex);
    }
    else {
      increment = 1;
    }
    int outputStartLine = Integer.parseInt(outputInfo);

    final FileInfo info = currentStratum.myFileId2Info.get(fileId);
    for (int i = 0; i < repeatCount; i++) {
      for (int j = i * increment; j < (i + 1) * increment; j++) {
        info.myOutput2InputLine.put(outputStartLine + j, inputStartLine + i);
      }
    }
  }

  private void parseFileInfo(final StratumInfo currentStratum, String fileInfo) throws IOException {
    String filePath = null;
    if (fileInfo.startsWith("+ ")) {
      fileInfo = fileInfo.substring(2);
      filePath = myReader.readLine();
    }
    final int index = fileInfo.indexOf(' ');
    String id = fileInfo.substring(0, index);
    String fileName = fileInfo.substring(index + 1);
    currentStratum.myFileId2Info.put(id, new FileInfo(fileName, filePath));
  }

  public String getOutputFileName() {
    return myOutputFileName;
  }

  public StratumInfo getStratum(@Nullable String id) {
    if (id == null) {
      id = myDefaultStratum;
    }
    StratumInfo stratumInfo = myId2Stratum.get(id);
    if (stratumInfo == null) {
      stratumInfo = myId2Stratum.get(myDefaultStratum);
    }
    return stratumInfo;
  }

  public static class StratumInfo {
    private final String myStratumId;
    private final Map<String, FileInfo> myFileId2Info = new HashMap<>();

    public StratumInfo(final String stratumId) {
      myStratumId = stratumId;
    }


    public String getStratumId() {
      return myStratumId;
    }

    public FileInfo[] getFileInfos() {
      final Collection<FileInfo> infos = myFileId2Info.values();
      return infos.toArray(new FileInfo[0]);
    }
  }

  public static class FileInfo {
    private final String myName;
    private final String myPath;
    private final BidirectionalMap<Integer, Integer> myOutput2InputLine = new BidirectionalMap<>();

    public FileInfo(final String name, final String path) {
      myName = name;
      myPath = path;
    }


    public String getName() {
      return myName;
    }

    public String getPath() {
      return myPath;
    }

    public List<Integer> getOutputLines(int inputLine) {
      return myOutput2InputLine.getKeysByValue(inputLine);
    }

    public int getInputLine(int outputLine) {
      final Integer line = myOutput2InputLine.get(outputLine);
      return line == null ? -1 : line;
    }
  }
}
