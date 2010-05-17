/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class ExceptionFilter implements Filter, DumbAware {
  private final Project myProject;
  @NonNls private static final String AT = "at";
  private static final String AT_PREFIX = AT + " ";
  private static final String STANDALONE_AT = " " + AT + " ";
  private static final TextAttributes HYPERLINK_ATTRIBUTES = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES);
  private final GlobalSearchScope mySearchScope;

  public ExceptionFilter(@NotNull final Project project) {
    myProject = project;
    mySearchScope = GlobalSearchScope.allScope(myProject);
  }

  public ExceptionFilter(@NotNull final GlobalSearchScope scope) {
    myProject = scope.getProject();
    mySearchScope = scope;
  }

  @Nullable
  static Trinity<String, String, TextRange> parseExceptionLine(final String line) {
    int atIndex;
    if (line.startsWith(AT_PREFIX)){
      atIndex = 0;
    }
    else{
      atIndex = line.indexOf(STANDALONE_AT);
      if (atIndex < 0) {
        atIndex = line.indexOf(AT_PREFIX);
      }
      if (atIndex < 0) return null;
    }

    final int lparenthIndex = line.indexOf('(', atIndex);
    if (lparenthIndex < 0) return null;
    final int lastDotIndex = line.lastIndexOf('.', lparenthIndex);
    if (lastDotIndex < 0 || lastDotIndex < atIndex) return null;
    String className = line.substring(atIndex + AT.length() + 1, lastDotIndex).trim();

    String methodName = line.substring(lastDotIndex + 1, lparenthIndex).trim();

    final int rparenthIndex = line.indexOf(')', lparenthIndex);
    if (rparenthIndex < 0) return null;

    return Trinity.create(className, methodName, new TextRange(lparenthIndex, rparenthIndex));
  }

  public Result applyFilter(final String line, final int textEndOffset) {
    final Trinity<String, String, TextRange> info = parseExceptionLine(line);
    if (info == null) {
      return null;
    }

    String className = info.first;
    final int dollarIndex = className.indexOf('$');
    if (dollarIndex >= 0){
      className = className.substring(0, dollarIndex);
    }

    final int lparenthIndex = info.third.getStartOffset();
    final int rparenthIndex = info.third.getEndOffset();
    final String fileAndLine = line.substring(lparenthIndex + 1, rparenthIndex).trim();

    final int colonIndex = fileAndLine.lastIndexOf(':');
    if (colonIndex < 0) return null;

    final String lineString = fileAndLine.substring(colonIndex + 1);
    try{
      final int lineNumber = Integer.parseInt(lineString);
      final PsiManager manager = PsiManager.getInstance(myProject);
      PsiClass aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(className, mySearchScope);
      if (aClass == null) {
        aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(className, GlobalSearchScope.allScope(myProject));
        if (aClass == null) return null;
      }
      final PsiFile file = (PsiFile) aClass.getContainingFile().getNavigationElement();
      if (file == null) return null;

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
      VirtualFile virtualFile = file.getVirtualFile();
      final OpenFileHyperlinkInfo linkInfo = new OpenFileHyperlinkInfo(myProject, virtualFile, lineNumber - 1);
      TextAttributes attributes = HYPERLINK_ATTRIBUTES.clone();
      if (!ProjectRootManager.getInstance(myProject).getFileIndex().isInContent(virtualFile)) {
        Color color = UIUtil.getTextInactiveTextColor();
        attributes.setForegroundColor(color);
        attributes.setEffectColor(color);
      }
      return new Result(highlightStartOffset, highlightEndOffset, linkInfo, attributes);
    }
    catch(NumberFormatException e){
      return null;
    }
  }

}