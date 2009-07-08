/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.ide.actions;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementFactoryImpl;
import com.intellij.codeInsight.template.*;
import com.intellij.ide.IdeBundle;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.ui.Messages;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The standard "New Class" action.
 *
 * @since 5.1
 */
public class CreateClassAction extends CreateInPackageFromTemplateActionBase {
  public CreateClassAction() {
    super(IdeBundle.message("action.create.new.class"),
          IdeBundle.message("action.create.new.class"), Icons.CLASS_ICON);
  }

  @NotNull
  protected PsiElement[] invokeDialog(Project project, PsiDirectory directory) {
    MyInputValidator validator = new MyInputValidator(project, directory);
    Messages.showInputDialog(project, IdeBundle.message("prompt.enter.new.class.name"),
                             IdeBundle.message("title.new.class"), Messages.getQuestionIcon(), "", validator);
    return validator.getCreatedElements();
  }

  protected String getCommandName() {
    return IdeBundle.message("command.create.class");
  }


  protected String getErrorTitle() {
    return IdeBundle.message("title.cannot.create.class");
  }


  protected String getActionName(PsiDirectory directory, String newName) {
    return IdeBundle.message("progress.creating.class", JavaDirectoryService.getInstance().getPackage(directory).getQualifiedName(), newName);
  }


  protected PsiClass doCreateClass(final PsiDirectory dir, final String className) throws IncorrectOperationException {
    return JavaDirectoryService.getInstance().createClass(dir, className);
  }

  protected Template buildTemplate(PsiClass templateClass) {
    final Project project = templateClass.getProject();
    TemplateBuilderImpl builder = new TemplateBuilderImpl(templateClass.getContainingFile());
    final ASTNode classToken = ObjectUtils.assertNotNull(templateClass.getNode()).findChildByType(JavaTokenType.CLASS_KEYWORD);
    assert classToken != null;
    builder.replaceElement(classToken.getPsi(), createTypeExpression(project), true);
    builder.setEndVariableBefore(templateClass.getLBrace());

    return builder.buildTemplate();
  }

  public static Expression createTypeExpression(final Project project) {
    return new Expression() {
      @Override
      public Result calculateResult(ExpressionContext context) {
        return new TextResult("class");
      }

      @Override
      public Result calculateQuickResult(ExpressionContext context) {
        return null;
      }

      @Override
      public LookupElement[] calculateLookupItems(ExpressionContext context) {
        final LookupElementFactoryImpl factory = LookupElementFactoryImpl.getInstance();
        List<LookupElement> items = new ArrayList<LookupElement>();
        items.add(factory.createLookupElement("class").setBold().setIcon(Icons.CLASS_ICON).setPriority(3));
        items.add(factory.createLookupElement("interface").setBold().setIcon(Icons.INTERFACE_ICON).setPriority(2));
        if (LanguageLevelProjectExtension.getInstance(project).getLanguageLevel().compareTo(LanguageLevel.JDK_1_5) >= 0) {
          items.add(factory.createLookupElement("enum").setBold().setIcon(Icons.ENUM_ICON).setPriority(1));
          items.add(factory.createLookupElement("@interface").setBold().setIcon(Icons.ANNOTATION_TYPE_ICON).setPriority(0));
        }
        return items.toArray(new LookupElement[items.size()]);
      }
    };
  }
}
