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
package com.intellij.pom.java.impl;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.pom.java.PomJavaAspect;
import com.intellij.pom.java.events.PomJavaAspectChangeSet;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.util.Collections;

public class PomJavaAspectImpl extends PomJavaAspect {
  private final PsiManager myPsiManager;
  private final PomModel myPomModel;

  public PomJavaAspectImpl(PsiManager psiManager, TreeAspect treeAspect, PomModel pomModel) {
    myPsiManager = psiManager;
    myPomModel = pomModel;
    pomModel.registerAspect(PomJavaAspect.class, this, Collections.singleton((PomModelAspect)treeAspect));
  }

  public LanguageLevel getLanguageLevel() {
    return LanguageLevelProjectExtension.getInstance(myPsiManager.getProject()).getLanguageLevel();
  }

  public void update(PomModelEvent event) {
    final TreeChangeEvent changeSet = (TreeChangeEvent)event.getChangeSet(myPomModel.getModelAspect(TreeAspect.class));
    if(changeSet == null) return;
    final PsiFile containingFile = changeSet.getRootElement().getPsi().getContainingFile();
    if(!(containingFile.getLanguage() instanceof JavaLanguage)) return;
    final PomJavaAspectChangeSet set = new PomJavaAspectChangeSet(myPomModel);
    event.registerChangeSet(this, set);
  }
}
