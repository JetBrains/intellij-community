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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vcs.contentAnnotation.VcsContentAnnotation;
import com.intellij.openapi.vcs.contentAnnotation.VcsContentAnnotationImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.BeforeAfter;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class ExceptionFilter implements Filter, DumbAware, FilterMixin {
  public static final Color CHANGED_BACKGROUND = new Color(188, 237, 201);
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
  static Trinity<TextRange, TextRange, TextRange> parseExceptionLine(final String line) {
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

    final int rparenthIndex = line.indexOf(')', lparenthIndex);
    if (rparenthIndex < 0) return null;

    // class, method, link
    return Trinity.create(adjustedRange(line, atIndex + AT.length() + 1, lastDotIndex),
                          adjustedRange(line, lastDotIndex + 1, lparenthIndex), new TextRange(lparenthIndex, rparenthIndex));
  }
  
  private static TextRange adjustedRange(final String line, final int start, final int end) {
    String sub = line.substring(start, end);
    return new TextRange(start, end - spacesEnd(sub));
  }
  
  private static int spacesStart(final String s) {
    int cnt = 0;
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      if (! Character.isSpaceChar(c)) return cnt;
      ++ cnt;
    }
    return 0;
  }
  private static int spacesEnd(final String s) {
    int cnt = 0;
    for (int i = s.length() - 1; i >= 0; i--) {
      final char c = s.charAt(i);
      if (! Character.isSpaceChar(c)) return cnt;
      ++ cnt;
    }
    return 0;
  }

  // todo do not work internal code
  @Override
  public void applyHeavyFilter(final String line, final int entireLength, int lineNumber, Consumer<AdditionalHighlight> consumer) {
    final MyWorker worker = new MyWorker();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        worker.execute(line, entireLength);
      }
    });
    if (worker.getResult() != null) {
      // find method range
      final PsiMethod[] methodsByName = worker.getPsiClass().findMethodsByName(worker.getMethod(), false);
      // todo also go up etc. now just take first
      if (methodsByName.length > 0) {

      }
      VcsContentAnnotation.Details details = VcsContentAnnotationImpl.getInstance(myProject)
        .annotateLine(worker.getFile().getVirtualFile(), new BeforeAfter<Integer>(-1, -1), lineNumber);
      if (details != null) {
        if (details.isFileChanged()) {
          final int textStartOffset = entireLength - line.length();
          int idx = line.indexOf(':', worker.getInfo().getThird().getStartOffset());
          int endIdx = idx == -1 ? worker.getInfo().getThird().getEndOffset() : idx;
          consumer.consume(new AdditionalHighlight(textStartOffset + worker.getInfo().getThird().getStartOffset() + 1,
                                                   textStartOffset + endIdx) {
            @Override
            public TextAttributes getTextAttributes(@Nullable TextAttributes source) {
              if (source == null) {
                TextAttributes atts =
                  EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.CLASS_NAME_ATTRIBUTES).clone();
                atts.setBackgroundColor(CHANGED_BACKGROUND);
                return atts;
              }
              TextAttributes clone = source.clone();
              clone.setBackgroundColor(CHANGED_BACKGROUND);
              return clone;
            }
          });
        }
        // todo also other
      }
    }
  }

  public Result applyFilter(final String line, final int textEndOffset) {
    final MyWorker worker = new MyWorker();
    worker.execute(line, textEndOffset);
    return worker.getResult();
  }

  private class MyWorker {
    private Result myResult;
    private PsiClass myClass;
    private PsiFile myFile;
    private String myMethod;
    private Trinity<TextRange,TextRange,TextRange> myInfo;

    public void execute(final String line, final int textEndOffset) {
      myInfo = parseExceptionLine(line);
      if (myInfo == null) {
        return;
      }

      myMethod = myInfo.getSecond().substring(line);
      String className = myInfo.first.substring(line).trim();
      final int dollarIndex = className.indexOf('$');
      if (dollarIndex >= 0){
        className = className.substring(0, dollarIndex);
      }

      final int lparenthIndex = myInfo.third.getStartOffset();
      final int rparenthIndex = myInfo.third.getEndOffset();
      final String fileAndLine = line.substring(lparenthIndex + 1, rparenthIndex).trim();

      final int colonIndex = fileAndLine.lastIndexOf(':');
      if (colonIndex < 0) return;

      final String lineString = fileAndLine.substring(colonIndex + 1);
      try{
        final int lineNumber = Integer.parseInt(lineString);
        final PsiManager manager = PsiManager.getInstance(myProject);
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(manager.getProject());
        myClass = psiFacade.findClass(className, mySearchScope);
        if (myClass == null) {
          myClass = psiFacade.findClass(className, GlobalSearchScope.allScope(myProject));
          if (myClass == null) {//try to find class according to all dollars in package name
            myClass = psiFacade.findClass(className, GlobalSearchScope.allScope(myProject));
          }
          if (myClass == null) return;
        }
        myFile = (PsiFile) myClass.getContainingFile().getNavigationElement();
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
        VirtualFile virtualFile = myFile.getVirtualFile();
        final OpenFileHyperlinkInfo linkInfo = new OpenFileHyperlinkInfo(myProject, virtualFile, lineNumber - 1);
        TextAttributes attributes = HYPERLINK_ATTRIBUTES.clone();
        if (!ProjectRootManager.getInstance(myProject).getFileIndex().isInContent(virtualFile)) {
          Color color = UIUtil.getInactiveTextColor();
          attributes.setForegroundColor(color);
          attributes.setEffectColor(color);
        }
        myResult = new Result(highlightStartOffset, highlightEndOffset, linkInfo, attributes);
      }
      catch(NumberFormatException e){
        //
      }
    }

    public Result getResult() {
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
  }
}