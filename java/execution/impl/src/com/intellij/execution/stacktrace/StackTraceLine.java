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
package com.intellij.execution.stacktrace;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;

public class StackTraceLine {
  private final Project myProject;
  private final String myLine;
  @NonNls
  protected static final String AT_STR = "at";
  protected static final String AT__STR = AT_STR + " ";
  @NonNls protected static final String INIT_MESSAGE = "<init>";

  public StackTraceLine(Project project, final String line) {
    myProject = project;
    myLine = line;
  }

  public String getClassName() {
    int index = myLine.indexOf(AT_STR);
    if (index < 0) return null;
    index += AT__STR.length();
    final int lastDot = getLastDot();
    if (lastDot < 0) return null;
    if (lastDot <= index) return null;
    return myLine.substring(index, lastDot);
  }

  private int getLastDot() {
    return myLine.lastIndexOf('.', getOpenBracket());
  }

  private int getOpenBracket() {
    return myLine.indexOf('(');
  }

  private int getCloseBracket() {
    return myLine.indexOf(')');
  }

  public int getLineNumber() throws NumberFormatException {
    final int close = getCloseBracket();
    final int lineNumberStart = myLine.lastIndexOf(':') + 1;
    if (close < 0 || lineNumberStart < 1 || lineNumberStart >= close) throw new NumberFormatException(myLine);
    return Integer.parseInt(myLine.substring(lineNumberStart, close)) - 1;
  }

  public OpenFileDescriptor getOpenFileDescriptor(final VirtualFile file) {
    final int lineNumber;
    try {
      lineNumber = getLineNumber();
    } catch(NumberFormatException e) {
      return new OpenFileDescriptor(myProject, file);
    }
    return new OpenFileDescriptor(myProject, file, lineNumber, 0);
  }

  public OpenFileDescriptor getOpenFileDescriptor(final Project project) {
    final Location<PsiMethod> location = getMethodLocation(project);
    if (location == null) return null;
    return getOpenFileDescriptor(location.getPsiElement().getContainingFile().getVirtualFile());
  }

  public String getMethodName() {
    final int lastDot = getLastDot();
    if (lastDot == -1) return null;
    return myLine.substring(getLastDot() + 1, getOpenBracket());
  }

  public Location<PsiMethod> getMethodLocation(final Project project) {
    String className = getClassName();
    final String methodName = getMethodName();
    if (className == null || methodName == null) return null;
    final int lineNumber;
    try {
      lineNumber = getLineNumber();
    } catch(NumberFormatException e) {
      return null;
    }
    final int dollarIndex = className.indexOf('$');
    if (dollarIndex != -1) className = className.substring(0, dollarIndex);
    PsiClass psiClass = findClass(project, className, lineNumber);
    if (psiClass == null || (psiClass.getNavigationElement() instanceof PsiCompiledElement)) return null;
    psiClass = (PsiClass)psiClass.getNavigationElement();
    final PsiMethod psiMethod = getMethodAtLine(psiClass, methodName, lineNumber);
    if (psiMethod != null) {
      return new MethodLineLocation(project, psiMethod, PsiLocation.fromPsiElement(psiClass), lineNumber);
    }
    else {
      return null;
    }
  }

  private static PsiClass findClass(final Project project, final String className, final int lineNumber) {
    if (project == null) return null;
    final PsiManager psiManager = PsiManager.getInstance(project);
    PsiClass psiClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(className, GlobalSearchScope.allScope(project));
    if (psiClass == null || (psiClass.getNavigationElement() instanceof PsiCompiledElement)) return null;
    psiClass = (PsiClass)psiClass.getNavigationElement();
    final PsiFile psiFile = psiClass.getContainingFile();
    return PsiTreeUtil.getParentOfType(psiFile.findElementAt(offsetOfLine(psiFile, lineNumber)), PsiClass.class, false);
  }

  private static PsiMethod getMethodAtLine(final PsiClass psiClass, final String methodName, final int lineNumber) {
    final PsiMethod[] methods;
    if (INIT_MESSAGE.equals(methodName)) methods = psiClass.getConstructors();
    else methods = psiClass.findMethodsByName(methodName, true);
    if (methods.length == 0) return null;
    final PsiFile psiFile = methods[0].getContainingFile();
    final int offset = offsetOfLine(psiFile, lineNumber);
    for (final PsiMethod method : methods) {
      if (method.getTextRange().contains(offset)) return method;
    }
    //if (!methods.hasNext() || location == null) return null;
    //return location.getPsiElement();

    //if ("<init>".equals(methodName)) methods = psiClass.getConstructors();
    //else methods = psiClass.findMethodsByName(methodName, true);
    //if (methods.length == 0) return null;
    //for (int i = 0; i < methods.length; i++) {
    //  PsiMethod method = methods[i];
    //  if (method.getTextRange().contains(offset)) return method;
    //}
    return null;
  }

  private static int offsetOfLine(final PsiFile psiFile, final int lineNumber) {
    final LineTokenizer lineTokenizer = new LineTokenizer(psiFile.getViewProvider().getContents());
    for (int i = 0; i < lineNumber; i++) lineTokenizer.advance();
    return lineTokenizer.getOffset();
  }
}
