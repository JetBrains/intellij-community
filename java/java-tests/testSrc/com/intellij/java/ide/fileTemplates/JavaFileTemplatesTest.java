// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.fileTemplates;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateAction;
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateGroup;
import com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.util.Arrays;

public class JavaFileTemplatesTest extends LightJavaCodeInsightFixtureTestCase {
  public void testCreateFromTemplateGroup() {
    myFixture.configureByText("foo.java", "");
    DataContext context = ((EditorEx)myFixture.getEditor()).getDataContext();
    AnActionEvent event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, context);
    AnAction[] children = new CreateFromTemplateGroup().getChildren(event);
    assertFalse(ContainerUtil.exists(children, action -> isTemplateAction(action, "Class")));
  }

  private static boolean isTemplateAction(AnAction action, String name) {
    return action instanceof CreateFromTemplateAction && name.equals(((CreateFromTemplateAction)action).getTemplate().getName());
  }

  public void testManyTemplates() {
    FileTemplateManagerImpl templateManager = (FileTemplateManagerImpl)FileTemplateManager.getInstance(getProject());
    templateManager.getState().recentTemplates.clear();
    FileTemplate[] before = templateManager.getAllTemplates();
    try {
      for (int i = 0; i < 30; i++) {
        templateManager.addTemplate("foo" + i, "java");
      }
      DataContext context = SimpleDataContext.getProjectContext(myFixture.getProject());
      AnActionEvent event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, context);
      AnAction[] children = new CreateFromTemplateGroup().getChildren(event);
      assertEquals(3, children.length);
      assertEquals(IdeBundle.message("action.from.file.template"), children[0].getTemplatePresentation().getText());
    }
    finally {
      templateManager.setTemplates(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY, Arrays.asList(before));
      templateManager.getState().recentTemplates.clear();
    }
  }
}