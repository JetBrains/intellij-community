// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixEditableTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JpsJavaSdkType;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class JavaEditablePostfixTemplateProvider extends JavaPostfixTemplateProvider
  implements PostfixEditableTemplateProvider<JavaEditablePostfixTemplate> {

  private static final String LANGUAGE_LEVEL_ATTR = "language-level";
  private static final String CONDITIONS_TAG = "conditions";
  private static final String TEMPLATE_TAG = "template";
  private static final String CONDITION_TAG = "condition";
  private static final String ID_ATTR = "id";
  private static final String FQN_ATTR = "fqn";
  public static final String TOPMOST_ATTR = "topmost";

  private HashSet<AssertStatementPostfixTemplate> myBuiltinTemplates = ContainerUtil.newHashSet(
    new AssertStatementPostfixTemplate(this)
  );

  @NotNull
  @Override
  public Set<? extends PostfixTemplate> getBuiltinTemplates() {
    return myBuiltinTemplates;
  }

  @NotNull
  @Override
  public String getId() {
    return "java";
  }

  @NotNull
  @Override
  public String getName() {
    return "Java";
  }

  @Nullable
  @Override
  public PostfixTemplateEditor<JavaEditablePostfixTemplate> createEditor(@Nullable Project project) {
    return new JavaPostfixTemplateEditor(project);
  }

  @NotNull
  @Override
  public JavaEditablePostfixTemplate readExternal(@NotNull String key, @NotNull Element template) {
    boolean useTopmostExpression = Boolean.parseBoolean(template.getAttributeValue(TOPMOST_ATTR));
    String languageLevelAttributeValue = template.getAttributeValue(LANGUAGE_LEVEL_ATTR);
    LanguageLevel languageLevel = ObjectUtils.notNull(LanguageLevel.parse(languageLevelAttributeValue), LanguageLevel.JDK_1_6);

    Set<JavaPostfixTemplateExpressionCondition> conditions = new LinkedHashSet<>();
    Element conditionsElement = template.getChild(CONDITIONS_TAG);
    if (conditionsElement != null) {
      for (Element conditionElement : conditionsElement.getChildren(CONDITION_TAG)) {
        ContainerUtil.addIfNotNull(conditions, readExternal(conditionElement));
      }
    }
    String templateText = template.getChildText(TEMPLATE_TAG);
    return new JavaEditablePostfixTemplate(key, conditions, languageLevel, useTopmostExpression, templateText, this);
  }

  @Override
  public void writeExternal(@NotNull PostfixTemplate template, @NotNull Element parentElement) {
    if (template instanceof JavaEditablePostfixTemplate) {
      parentElement.setAttribute("topmost", String.valueOf(((JavaEditablePostfixTemplate)template).isUseTopmostExpression()));

      LanguageLevel languageLevel = ((JavaEditablePostfixTemplate)template).getMinimumLanguageLevel();
      parentElement.setAttribute(LANGUAGE_LEVEL_ATTR, JpsJavaSdkType.complianceOption(languageLevel.toJavaVersion()));
      Element conditions = parentElement.addContent(CONDITIONS_TAG);
      for (JavaPostfixTemplateExpressionCondition condition : ((JavaEditablePostfixTemplate)template).getExpressionConditions()) {
        writeExternal(condition, conditions);
      }
      parentElement.addContent(TEMPLATE_TAG).setText(((JavaEditablePostfixTemplate)template).getTemplateText());
    }
  }

  @Nullable
  public JavaPostfixTemplateExpressionCondition readExternal(@NotNull Element condition) {
    String id = condition.getAttributeValue(ID_ATTR);
    if (JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateArrayExpressionCondition.ID.equals(id)) {
      return new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateArrayExpressionCondition();
    }
    if (JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNonVoidExpressionCondition.ID.equals(id)) {
      return new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNonVoidExpressionCondition();
    }
    if (JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateVoidExpressionCondition.ID.equals(id)) {
      return new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateVoidExpressionCondition();
    }
    if (JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateBooleanExpressionCondition.ID.equals(id)) {
      return new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateBooleanExpressionCondition();
    }
    if (JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition.ID.equals(id)) {
      String fqn = condition.getAttributeValue(FQN_ATTR);
      if (StringUtil.isNotEmpty(fqn)) {
        return new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition(fqn);
      }
    }
    return null;
  }

  public void writeExternal(@NotNull JavaPostfixTemplateExpressionCondition condition, @NotNull Element parentElement) {
    Element element = parentElement.addContent(CONDITION_TAG);
    element.setAttribute(ID_ATTR, condition.getId());
    if (condition instanceof JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition) {
      String fqn = ((JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition)condition).getFqn();
      element.setAttribute(FQN_ATTR, fqn);
    }
  }
}
