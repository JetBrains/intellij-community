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
package com.intellij.compiler.impl.javaCompiler.eclipse;

import com.intellij.compiler.OutputParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NonNls;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EclipseCompilerErrorParser extends OutputParser {

  private final StringBuilder problemText = new StringBuilder();

  public boolean processMessageLine(Callback callback) {
    @NonNls String line = callback.getNextLine();
    if (line == null) {
      spitOutProblem(callback);
      return false;
    }
    if (line.trim().length() == 0) {
      return true;
    }
    if (line.equals("----------")) {
      spitOutProblem(callback);
      problemText.setLength(0);
      return true;
    }
    problemText.append(line);
    problemText.append("\n");
    return true;
  }

  private void spitOutProblem(final Callback callback) {
    final String problem = problemText.toString();
    if (problem.trim().length() == 0) return;

    @NonNls final String problemTemplate = "(\\d*)\\. (\\w*) in (.*)" +
                                           "\\s*\\(at line (\\d*)\\)\n" +
                                           "\\s*(.*)\n" +
                                           "(\\s*)\\^+\n" +
                                           "(.*)\\s*";
    final Pattern PATTERN = Pattern.compile(problemTemplate, Pattern.DOTALL);
    Matcher matcher = PATTERN.matcher(problem);
    if (matcher.matches()) {
      //String seqN = matcher.group(1);
      @NonNls String problemType = matcher.group(2);
      String path = matcher.group(3).trim();
      String lineNum = matcher.group(4);
      String codeSnippet = matcher.group(5);
      String indentWhiteSpace = matcher.group(6);
      String message = matcher.group(7).trim();

      CompilerMessageCategory messageCategory;
      if ("WARNING".equals(problemType)) {
        messageCategory = CompilerMessageCategory.WARNING;
      }
      else if ("ERROR".equals(problemType)) {
        messageCategory = CompilerMessageCategory.ERROR;
      }
      else {
        messageCategory = CompilerMessageCategory.INFORMATION;
      }
      final String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, FileUtil.toSystemIndependentName(path));
      final int line = Integer.parseInt(lineNum);
      int col = indentWhiteSpace.length();
      final String offendingCode = codeSnippet.substring(col-1);

      // try to find similar text inside source file                   
      int colFromFile = ApplicationManager.getApplication().runReadAction(new Computable<Integer>() {
        public Integer compute() {
          int index = -1;
          VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
          Document document = file == null ? null : FileDocumentManager.getInstance().getDocument(file);
          if (document != null) {
            // line is one-based
            int docLine = line == 0 ? 0 : line-1;
            int startOffset = document.getLineStartOffset(docLine);
            int endOffset = document.getLineEndOffset(docLine);
            String lineText = document.getText().substring(startOffset, endOffset);
            index = lineText.indexOf(offendingCode);
            if (index == -1) {
              for (index = 0; index < lineText.length(); index++) {
                if (!Character.isWhitespace(lineText.charAt(index))) break;
              }
              if (index == lineText.length()) index = -1;
            }
            // to one-based
            if (index != -1) {
              index++;
            }
          }
          return index;
        }
      }).intValue();
      if (colFromFile != -1) {
        col = colFromFile;
      }
      callback.message(messageCategory, message, url, line, col);
    }
    else {
      callback.message(CompilerMessageCategory.WARNING, problem, null, -1, -1);
    }
  }

  public boolean isTrimLines() {
    return false;
  }
}
