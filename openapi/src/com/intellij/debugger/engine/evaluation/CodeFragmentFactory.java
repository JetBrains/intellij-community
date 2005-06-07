/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.engine.evaluation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;

import java.util.List;
import java.util.ArrayList;

public interface CodeFragmentFactory extends ApplicationComponent {

  PsiCodeFragment createCodeFragment(TextWithImports item, PsiElement context, Project project);

  boolean isContextAccepted(PsiElement contextElement);
}
