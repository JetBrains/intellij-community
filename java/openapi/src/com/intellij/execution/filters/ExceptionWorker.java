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
import com.intellij.openapi.util.Trinity;
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

/**
 * User: Irina.Chernushina
 * Date: 8/5/11
 * Time: 8:36 PM
 */
public class ExceptionWorker {
  @NonNls private static final String AT = "at";
  private static final String AT_PREFIX = AT + " ";
  private static final String STANDALONE_AT = " " + AT + " ";

  private final Project myProject;
  private Filter.Result myResult;
  private PsiClass[] myClasses = PsiClass.EMPTY_ARRAY;
  private PsiFile[] myFiles = PsiFile.EMPTY_ARRAY;
  private String myMethod;
  private Trinity<TextRange, TextRange, TextRange> myInfo;
  private final ExceptionInfoCache myCache;

  public ExceptionWorker(@NotNull ExceptionInfoCache cache) {
    myProject = cache.getProject();
    myCache = cache;
  }

  public void execute(final String line, final int textEndOffset) {
    myResult = null;
    myInfo = parseExceptionLine(line);
    if (myInfo == null) {
      return;
    }

    myMethod = myInfo.getSecond().substring(line);

    final int lparenthIndex = myInfo.third.getStartOffset();
    final int rparenthIndex = myInfo.third.getEndOffset();
    final String fileAndLine = line.substring(lparenthIndex + 1, rparenthIndex).trim();

    final int colonIndex = fileAndLine.lastIndexOf(':');
    if (colonIndex < 0) return;

    final int lineNumber = getLineNumber(fileAndLine.substring(colonIndex + 1));
    if (lineNumber < 0) return;

    Pair<PsiClass[], PsiFile[]> pair = myCache.resolveClass(myInfo.first.substring(line).trim());
    myClasses = pair.first;
    myFiles = pair.second;
    if (myFiles.length == 0) {
      // try find the file with the required name
      //todo[nik] it would be better to use FilenameIndex here to honor the scope by it isn't accessible in Open API
      myFiles = PsiShortNamesCache.getInstance(myProject).getFilesByName(fileAndLine.substring(0, colonIndex).trim());
    }
    if (myFiles.length == 0) return;

    /*
     IDEADEV-4976: Some scramblers put something like SourceFile mock instead of real class name.
    final String filePath = fileAndLine.substring(0, colonIndex).replace('/', File.separatorChar);
    final int slashIndex = filePath.lastIndexOf(File.separatorChar);
    final String shortFileName = slashIndex < 0 ? filePath : filePath.substring(slashIndex + 1);
    if (!file.getName().equalsIgnoreCase(shortFileName)) return null;
    */

    final int textStartOffset = textEndOffset - line.length();

    final int highlightStartOffset = textStartOffset + lparenthIndex + 1;
    final int highlightEndOffset = textStartOffset + rparenthIndex;

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
    HyperlinkInfo linkInfo = HyperlinkInfoFactory.getInstance().createMultipleFilesHyperlinkInfo(virtualFiles, lineNumber - 1, myProject);
    myResult = new Filter.Result(highlightStartOffset, highlightEndOffset, linkInfo, attributes);
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

  public Trinity<TextRange, TextRange, TextRange> getInfo() {
    return myInfo;
  }

  //todo [roma] regexp
  @Nullable
  static Trinity<TextRange, TextRange, TextRange> parseExceptionLine(final String line) {
    int startIdx;
    if (line.startsWith(AT_PREFIX)){
      startIdx = 0;
    }
    else{
      startIdx = line.indexOf(STANDALONE_AT);
      if (startIdx < 0) {
        startIdx = line.indexOf(AT_PREFIX);
      }

      if (startIdx < 0) {
        startIdx = -1;
      }
    }

    int rParenIdx = line.lastIndexOf(')');
    while (rParenIdx > 0 && !Character.isDigit(line.charAt(rParenIdx-1))) {
      rParenIdx = line.lastIndexOf(')', rParenIdx - 1);
    }
    if (rParenIdx < 0) return null;

    final int lParenIdx = line.lastIndexOf('(', rParenIdx);
    if (lParenIdx < 0) return null;
    
    final int dotIdx = line.lastIndexOf('.', lParenIdx);
    if (dotIdx < 0 || dotIdx < startIdx) return null;

    // class, method, link
    return Trinity.create(new TextRange(startIdx + 1 + (startIdx >= 0 ? AT.length() : 0), handleSpaces(line, dotIdx, -1, true)),
                          new TextRange(handleSpaces(line, dotIdx + 1, 1, true), handleSpaces(line, lParenIdx + 1, -1, true)),
                          new TextRange(lParenIdx, rParenIdx));
  }

  private static int handleSpaces(String line, int pos, int delta, boolean skip) {
    int len = line.length();
    while (pos >= 0 && pos < len) {
      final char c = line.charAt(pos);
      if (skip != Character.isSpaceChar(c)) break;
      pos += delta;
    }
    return pos;
  }
}
