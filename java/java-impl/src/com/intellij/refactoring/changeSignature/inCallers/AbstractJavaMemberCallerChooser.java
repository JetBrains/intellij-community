/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature.inCallers;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.changeSignature.CallerChooserBase;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nls;

import java.util.Set;

public abstract class AbstractJavaMemberCallerChooser<M extends PsiMember> extends CallerChooserBase<M> {

  public AbstractJavaMemberCallerChooser(M member, Project project, @Nls(capitalization = Nls.Capitalization.Title) String title, Tree previousTree, Consumer<? super Set<M>> callback) {
    super(member, project, title, previousTree, "dummy." + JavaFileType.INSTANCE.getDefaultExtension(), callback);
  }
}
