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
package com.intellij.usages.impl.rules;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.jsp.JspFile;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;

public class NonJavaFileGroupingRule extends FileGroupingRule {
  public NonJavaFileGroupingRule(Project project) {
    super(project);
  }

  public UsageGroup groupUsage(Usage usage) {
    final FileUsageGroup usageGroup = (FileUsageGroup)super.groupUsage(usage);
    if (usageGroup != null) {
      final PsiFile psiFile = usageGroup.getPsiFile();
      if (psiFile instanceof PsiJavaFile && !(psiFile instanceof JspFile)) {
        return null;
      }
    }
    return usageGroup;
  }

}
