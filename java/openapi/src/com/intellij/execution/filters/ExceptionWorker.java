/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * User: Irina.Chernushina
 * Date: 8/5/11
 * Time: 8:36 PM
 */
public class ExceptionWorker {
  @NonNls private static final String AT = "at";
  private static final String AT_PREFIX = AT + " ";
  private static final String STANDALONE_AT = " " + AT + " ";

  private static final TextAttributes HYPERLINK_ATTRIBUTES;
  private static final TextAttributes LIBRARY_HYPERLINK_ATTRIBUTES;
  
  static {
    HYPERLINK_ATTRIBUTES = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES);
    
    LIBRARY_HYPERLINK_ATTRIBUTES = HYPERLINK_ATTRIBUTES.clone();
    Color libTextColor = UIUtil.getInactiveTextColor();
    LIBRARY_HYPERLINK_ATTRIBUTES.setForegroundColor(libTextColor);
    LIBRARY_HYPERLINK_ATTRIBUTES.setEffectColor(libTextColor);
  }

  private final Project myProject;
  private final GlobalSearchScope mySearchScope;
  private Filter.Result myResult;
  private PsiClass myClass;
  private PsiFile myFile;
  private String myMethod;
  private Trinity<TextRange, TextRange, TextRange> myInfo;

  public ExceptionWorker(@NotNull Project project, @NotNull GlobalSearchScope searchScope) {
    myProject = project;
    mySearchScope = searchScope;
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

    final String lineString = fileAndLine.substring(colonIndex + 1);
    try {
      final int lineNumber = Integer.parseInt(lineString);
      myClass = findPositionClass(line);
      myFile = myClass == null ? null : (PsiFile)myClass.getContainingFile().getNavigationElement();
      if (myFile == null) {
        // try find the file with the required name
        PsiFile[] files = PsiShortNamesCache.getInstance(myProject).getFilesByName(fileAndLine.substring(0, colonIndex).trim());
        if (files.length > 0) {
          myFile = files[0];
        }
      }
      if (myFile == null) return;

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
      final VirtualFile virtualFile = myFile.getVirtualFile();

      HyperlinkInfo linkInfo = new MyHyperlinkInfo(myProject, virtualFile, lineNumber);

      boolean inContent = ProjectRootManager.getInstance(myProject).getFileIndex().isInContent(virtualFile);
      TextAttributes attributes = inContent ? HYPERLINK_ATTRIBUTES : LIBRARY_HYPERLINK_ATTRIBUTES;
      myResult = new Filter.Result(highlightStartOffset, highlightEndOffset, linkInfo, attributes);
    }
    catch (NumberFormatException e) {
      //
    }
  }

  private PsiClass findPositionClass(String line) {
    String className = myInfo.first.substring(line).trim();
    PsiClass result = findClassPreferringMyScope(className);
    if (result == null) {
      final int dollarIndex = className.indexOf('$');
      if (dollarIndex >= 0) {
        result = findClassPreferringMyScope(className.substring(0, dollarIndex));
      }
    }
    return result;
  }

  private PsiClass findClassPreferringMyScope(String className) {
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
    PsiClass result = psiFacade.findClass(className, mySearchScope);
    return result != null ? result : psiFacade.findClass(className, GlobalSearchScope.allScope(myProject));
  }

  public Filter.Result getResult() {
    return myResult;
  }

  public PsiClass getPsiClass() {
    return myClass;
  }

  public String getMethod() {
    return myMethod;
  }

  public PsiFile getFile() {
    return myFile;
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

    final int lparenIdx = line.indexOf('(', startIdx);
    if (lparenIdx < 0) return null;
    final int dotIdx = line.lastIndexOf('.', lparenIdx);
    if (dotIdx < 0 || dotIdx < startIdx) return null;

    final int rparenIdx = line.indexOf(')', lparenIdx);
    if (rparenIdx < 0) return null;

    // class, method, link
    return Trinity.create(new TextRange(startIdx + 1 + (startIdx >= 0 ? AT.length() : 0), handleSpaces(line, dotIdx, -1, true)),
                          new TextRange(handleSpaces(line, dotIdx + 1, 1, true), handleSpaces(line, lparenIdx + 1, -1, true)),
                          new TextRange(lparenIdx, rparenIdx));
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

  @Nullable
  static OpenFileHyperlinkInfo getOpenFileHyperlinkInfo(Filter.Result result) {
    if (result.hyperlinkInfo instanceof MyHyperlinkInfo) {
      MyHyperlinkInfo info = (MyHyperlinkInfo)result.hyperlinkInfo;
      return new OpenFileHyperlinkInfo(info.myProject, info.myVirtualFile, info.myLineNumber);
    }
    return null;
  }

  private static class MyHyperlinkInfo implements FileHyperlinkInfo {
    private final VirtualFile myVirtualFile;
    private final int myLineNumber;
    private final Project myProject;

    public MyHyperlinkInfo(@NotNull Project project, @NotNull VirtualFile virtualFile, int lineNumber) {
      myProject = project;
      myVirtualFile = virtualFile;
      myLineNumber = lineNumber;
    }

    @Override
    public void navigate(Project project) {
      VirtualFile currentVirtualFile = null;

      AccessToken accessToken = ReadAction.start();

      try {
        if (!myVirtualFile.isValid()) return;

        PsiFile psiFile = PsiManager.getInstance(project).findFile(myVirtualFile);
        if (psiFile != null) {
          PsiElement navigationElement = psiFile.getNavigationElement(); // Sources may be downloaded.
          if (navigationElement instanceof PsiFile) {
            currentVirtualFile = ((PsiFile)navigationElement).getVirtualFile();
          }
        }

        if (currentVirtualFile == null) {
          currentVirtualFile = myVirtualFile;
        }
      }
      finally {
        accessToken.finish();
      }

      new OpenFileHyperlinkInfo(myProject, currentVirtualFile, myLineNumber - 1).navigate(project);
    }

    @Nullable
    @Override
    public OpenFileDescriptor getDescriptor() {
      return new OpenFileDescriptor(myProject, myVirtualFile, myLineNumber - 1, 0);
    }
  }
}
