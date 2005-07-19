/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;

import java.io.File;

public class ExceptionFilter implements Filter{
  private final Project myProject;

  public ExceptionFilter(final Project project) {
    myProject = project;
  }

  public Result applyFilter(final String line, final int entireLength) {
    int atIndex;
    if (line.startsWith("at ")){
      atIndex = 0;
    }
    else{
      atIndex = line.indexOf("at ");
      if (atIndex < 0) {
        atIndex = line.indexOf(" at ");
      }
      if (atIndex < 0) return null;
    }

    final int lparenthIndex = line.indexOf('(', atIndex);
    if (lparenthIndex < 0) return null;
    final int lastDotIndex = line.lastIndexOf('.', lparenthIndex);
    if (lastDotIndex < 0 || lastDotIndex < atIndex) return null;
    String className = line.substring(atIndex + "at".length() + 1, lastDotIndex).trim();
    final int dollarIndex = className.indexOf('$');
    if (dollarIndex >= 0){
      className = className.substring(0, dollarIndex);
    }

    //String methodName = text.substring(lastDotIndex + 1, lparenthIndex).trim();

    final int rparenthIndex = line.indexOf(')', lparenthIndex);
    if (rparenthIndex < 0) return null;

    final String fileAndLine = line.substring(lparenthIndex + 1, rparenthIndex).trim();

    final int colonIndex = fileAndLine.lastIndexOf(':');
    if (colonIndex < 0) return null;

    final String lineString = fileAndLine.substring(colonIndex + 1);
    final String filePath = fileAndLine.substring(0, colonIndex).replace('/', File.separatorChar);
    try{
      final int lineNumber = Integer.parseInt(lineString);
      final String className1 = className;
      final PsiManager manager = PsiManager.getInstance(myProject);
      PsiClass aClass = manager.findClass(className1, GlobalSearchScope.allScope(myProject));
      if (aClass == null) return null;
      aClass = (PsiClass) aClass.getNavigationElement();
      if (aClass == null) return null;
      final PsiFile file = aClass.getContainingFile();
      final int slashIndex = filePath.lastIndexOf(File.separatorChar);
      final String shortFileName = slashIndex < 0 ? filePath : filePath.substring(slashIndex + 1);
      if (!file.getName().equalsIgnoreCase(shortFileName)) return null;

      final int textStartOffset = entireLength - line.length();
      final int highlightStartOffset = textStartOffset + lparenthIndex + 1;
      final int highlightEndOffset = textStartOffset + rparenthIndex;
      final OpenFileHyperlinkInfo info = new OpenFileHyperlinkInfo(myProject, file.getVirtualFile(), lineNumber - 1);
      return new Result(highlightStartOffset, highlightEndOffset, info);
    }
    catch(NumberFormatException e){
      return null;
    }
  }
}