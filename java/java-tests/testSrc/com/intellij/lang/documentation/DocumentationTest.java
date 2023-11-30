// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation;

import com.intellij.ide.DataManager;
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.EdtDataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

import static com.intellij.openapi.actionSystem.impl.Utils.createAsyncDataContext;
import static com.intellij.platform.ide.documentation.ActionsKt.DOCUMENTATION_TARGETS;

public final class DocumentationTest extends LightJavaCodeInsightTestCase {

  public void testDocumentationTargetsCanBeObtainedFromEdtContext() {
    configureFromFileText("A.java", "class <caret>A {}");
    DataContext dc = new EdtDataContext(getContextComponent());
    List<DocumentationTarget> targets = dc.getData(DOCUMENTATION_TARGETS);
    assertInstanceOf(assertOneElement(targets), PsiElementDocumentationTarget.class);
  }

  public void testDocumentationTargetsCanBeObtainedFromPreCachedContext2() {
    configureFromFileText("A.java", "class <caret>A {}");
    DataContext dc = createAsyncDataContext(new EdtDataContext(getContextComponent()));
    List<DocumentationTarget> targets = dc.getData(DOCUMENTATION_TARGETS);
    assertInstanceOf(assertOneElement(targets), PsiElementDocumentationTarget.class);
  }

  private @NotNull JComponent getContextComponent() {
    Editor editor = getEditor();
    JPanel parent = new JPanel();
    parent.add(editor.getComponent());
    DataManager.registerDataProvider(parent, dataId -> {
      return CommonDataKeys.PROJECT.is(dataId) ? getProject() : null;
    });
    return editor.getContentComponent();
  }
}
