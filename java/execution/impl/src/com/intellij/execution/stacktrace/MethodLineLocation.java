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
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

public class MethodLineLocation extends MethodLocation {
  private final int myLineNumber;

  public MethodLineLocation(final Project project, final PsiMethod method, final Location<PsiClass> classLocation, final int lineNumber) {
    super(project, method, classLocation);
    myLineNumber = lineNumber;
  }

  public OpenFileDescriptor getOpenFileDescriptor() {
    final VirtualFile virtualFile = getContainingClass().getContainingFile().getVirtualFile();
    return new OpenFileDescriptor(getProject(), virtualFile, myLineNumber, 0);
  }
}
