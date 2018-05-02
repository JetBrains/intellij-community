/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.filters;

import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ExceptionWorker {
  @NonNls private static final String AT = "at";
  private static final String AT_PREFIX = AT + " ";
  private static final String STANDALONE_AT = " " + AT + " ";

  private final Project myProject;
  private Filter.Result myResult;
  private PsiClass[] myClasses = PsiClass.EMPTY_ARRAY;
  private PsiFile[] myFiles = PsiFile.EMPTY_ARRAY;
  private String myMethod;
  private ParsedLine myInfo;
  private final ExceptionInfoCache myCache;

  public ExceptionWorker(@NotNull ExceptionInfoCache cache) {
    myProject = cache.getProject();
    myCache = cache;
  }

  public Filter.Result execute(final String line, final int textEndOffset) {
    myResult = null;
    myInfo = parseExceptionLine(line);
    if (myInfo == null) {
      return null;
    }

    myMethod = myInfo.methodNameRange.substring(line);

    Pair<PsiClass[], PsiFile[]> pair = myCache.resolveClass(myInfo.classFqnRange.substring(line).trim());
    myClasses = pair.first;
    myFiles = pair.second;
    if (myFiles.length == 0 && myInfo.fileName != null) {
      // try find the file with the required name
      //todo[nik] it would be better to use FilenameIndex here to honor the scope by it isn't accessible in Open API
      myFiles = PsiShortNamesCache.getInstance(myProject).getFilesByName(myInfo.fileName);
    }
    if (myFiles.length == 0) return null;

    /*
     IDEADEV-4976: Some scramblers put something like SourceFile mock instead of real class name.
    final String filePath = fileAndLine.substring(0, colonIndex).replace('/', File.separatorChar);
    final int slashIndex = filePath.lastIndexOf(File.separatorChar);
    final String shortFileName = slashIndex < 0 ? filePath : filePath.substring(slashIndex + 1);
    if (!file.getName().equalsIgnoreCase(shortFileName)) return null;
    */

    final int textStartOffset = textEndOffset - line.length();

    int highlightStartOffset = textStartOffset + myInfo.fileLineRange.getStartOffset();
    int highlightEndOffset = textStartOffset + myInfo.fileLineRange.getEndOffset();

    ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    List<VirtualFile> virtualFilesInLibraries = new ArrayList<>();
    List<VirtualFile> virtualFilesInContent = new ArrayList<>();
    for (PsiFile file : myFiles) {
      VirtualFile virtualFile = file.getVirtualFile();
      if (index.isInContent(virtualFile)) {
        virtualFilesInContent.add(virtualFile);
      }
      else {
        virtualFilesInLibraries.add(virtualFile);
      }
    }

    List<VirtualFile> virtualFiles;
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES);
    if (virtualFilesInContent.isEmpty()) {
      Color libTextColor = UIUtil.getInactiveTextColor();
      attributes = attributes.clone();
      attributes.setForegroundColor(libTextColor);
      attributes.setEffectColor(libTextColor);

      virtualFiles = virtualFilesInLibraries;
    }
    else {
      virtualFiles = virtualFilesInContent;
    }
    HyperlinkInfo linkInfo = HyperlinkInfoFactory.getInstance().createMultipleFilesHyperlinkInfo(virtualFiles, myInfo.lineNumber - 1, myProject);
    Filter.Result result = new Filter.Result(highlightStartOffset, highlightEndOffset, linkInfo, attributes);
    myResult = result;
    return result;
  }

  private static int getLineNumber(String lineString) {
    // some quick checks to avoid costly exceptions
    if (lineString.isEmpty() || lineString.length() > 9 || !Character.isDigit(lineString.charAt(0))) {
      return -1;
    }

    try {
      return Integer.parseInt(lineString);
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  public Filter.Result getResult() {
    return myResult;
  }

  public PsiClass getPsiClass() {
    return ArrayUtil.getFirstElement(myClasses);
  }

  public String getMethod() {
    return myMethod;
  }

  public PsiFile getFile() {
    return ArrayUtil.getFirstElement(myFiles);
  }

  public ParsedLine getInfo() {
    return myInfo;
  }

  private static int findAtPrefix(String line) {
    if (line.startsWith(AT_PREFIX)) return 0;

    int startIdx = line.indexOf(STANDALONE_AT);
    return startIdx < 0 ? line.indexOf(AT_PREFIX) : startIdx;
  }

  private static int findFirstRParenAfterDigit(String line) {
    int rParenIdx = -1;
    int rParenCandidate = line.lastIndexOf(')');
    //Looking for minimal position for ')' after a digit
    while (rParenCandidate > 0) {
      if (Character.isDigit(line.charAt(rParenCandidate - 1))) {
        rParenIdx = rParenCandidate;
      }
      rParenCandidate = line.lastIndexOf(')', rParenCandidate - 1);
    }
    return rParenIdx;
  }

  @Nullable
  public static ParsedLine parseExceptionLine(final String line) {
    ParsedLine result = parseNormalStackTraceLine(line);
    if (result == null) result = parseYourKitLine(line);
    if (result == null) result = parseForcedLine(line);
    return result;
  }

  @Nullable
  private static ParsedLine parseNormalStackTraceLine(String line) {
    int startIdx = findAtPrefix(line);
    int rParenIdx = findFirstRParenAfterDigit(line);
    if (rParenIdx < 0) return null;

    TextRange methodName = findMethodNameCandidateBefore(line, startIdx, rParenIdx);
    if (methodName == null) return null;

    int lParenIdx = methodName.getEndOffset();
    int dotIdx = methodName.getStartOffset() - 1;
    int moduleIdx = line.indexOf('/');
    int classNameIdx = moduleIdx > -1 && moduleIdx < dotIdx ? moduleIdx + 1 : startIdx + 1 + (startIdx >= 0 ? AT.length() : 0);

    return ParsedLine.createFromFileAndLine(new TextRange(classNameIdx, handleSpaces(line, dotIdx, -1)),
                                            trimRange(line, methodName),
                                            lParenIdx + 1, rParenIdx, line);
  }

  private static TextRange trimRange(String line, TextRange range) {
    int start = handleSpaces(line, range.getStartOffset(), 1);
    int end = handleSpaces(line, range.getEndOffset(), -1);
    if (start != range.getStartOffset() || end != range.getEndOffset()) {
      return TextRange.create(start, end);
    }
    return range;
  }

  @Nullable
  private static ParsedLine parseYourKitLine(String line) {
    int lineEnd = line.length() - 1;
    if (lineEnd > 0 && line.charAt(lineEnd) == '\n') lineEnd--;
    if (lineEnd > 0 && Character.isDigit(line.charAt(lineEnd))) {
      int spaceIndex = line.lastIndexOf(' ');
      int rParenIdx = line.lastIndexOf(')');
      if (rParenIdx > 0 && spaceIndex == rParenIdx + 1) {
        TextRange methodName = findMethodNameCandidateBefore(line, 0, rParenIdx);
        if (methodName != null) {
          return ParsedLine.createFromFileAndLine(new TextRange(0, methodName.getStartOffset() - 1),
                                                  methodName,
                                                  spaceIndex + 1, lineEnd + 1, 
                                                  line);
        }
      }
    }
    return null;
  }

  @Nullable
  private static ParsedLine parseForcedLine(String line) {
    String dash = "- ";
    if (!line.trim().startsWith(dash)) return null;

    String linePrefix = "line=";
    int lineNumberStart = line.indexOf(linePrefix);
    if (lineNumberStart < 0) return null;
    
    int lineNumberEnd = line.indexOf(' ', lineNumberStart);
    if (lineNumberEnd < 0) return null;

    TextRange methodName = findMethodNameCandidateBefore(line, 0, lineNumberStart);
    if (methodName == null) return null;

    int lineNumber = getLineNumber(line.substring(lineNumberStart + linePrefix.length(), lineNumberEnd));
    if (lineNumber < 0) return null;

    return new ParsedLine(trimRange(line, TextRange.create(line.indexOf(dash) + dash.length(), methodName.getStartOffset() - 1)), 
                          methodName, 
                          TextRange.create(lineNumberStart, lineNumberEnd), null, lineNumber);
  }
  
  private static TextRange findMethodNameCandidateBefore(String line, int start, int end) {
    int lParenIdx = line.lastIndexOf('(', end);
    if (lParenIdx < 0) return null;

    int dotIdx = line.lastIndexOf('.', lParenIdx);
    if (dotIdx < 0 || dotIdx < start) return null;
    
    return TextRange.create(dotIdx + 1, lParenIdx);
  }

  private static int handleSpaces(String line, int pos, int delta) {
    int len = line.length();
    while (pos >= 0 && pos < len) {
      final char c = line.charAt(pos);
      if (!Character.isSpaceChar(c)) break;
      pos += delta;
    }
    return pos;
  }

  public static class ParsedLine {
    @NotNull public final TextRange classFqnRange;
    @NotNull public final TextRange methodNameRange;
    @NotNull public final TextRange fileLineRange;
    @Nullable public final String fileName;
    public final int lineNumber;

    ParsedLine(@NotNull TextRange classFqnRange,
                      @NotNull TextRange methodNameRange,
                      @NotNull TextRange fileLineRange, @Nullable String fileName, int lineNumber) {
      this.classFqnRange = classFqnRange;
      this.methodNameRange = methodNameRange;
      this.fileLineRange = fileLineRange;
      this.fileName = fileName;
      this.lineNumber = lineNumber;
    }

    @Nullable
    private static ParsedLine createFromFileAndLine(@NotNull TextRange classFqnRange,
                                                              @NotNull TextRange methodNameRange,
                                                              int fileLineStart, int fileLineEnd, String line) {
      TextRange fileLineRange = TextRange.create(fileLineStart, fileLineEnd);
      String fileAndLine = fileLineRange.substring(line);

      int colonIndex = fileAndLine.lastIndexOf(':');
      if (colonIndex < 0) return null;

      int lineNumber = getLineNumber(fileAndLine.substring(colonIndex + 1));
      if (lineNumber < 0) return null;

      return new ParsedLine(classFqnRange, methodNameRange, fileLineRange, fileAndLine.substring(0, colonIndex).trim(), lineNumber);
    } 
  }
}
