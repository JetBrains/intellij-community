// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.internal.statistic.beans.MetricEventUtilKt.addBoolIfDiffers;
import static com.intellij.internal.statistic.beans.MetricEventUtilKt.addMetricIfDiffers;

@NonNls
public class JavaRefactoringUsageCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "java.refactoring.settings";
  }

  @Override
  public int getVersion() {
    return 4;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics() {
    Set<MetricEvent> result = new HashSet<>();
    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    JavaRefactoringSettings defaultSettings = new JavaRefactoringSettings();

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_IN_COMMENTS_FOR_FIELD, "rename.search.in.comments.for.field");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD, "rename.search.in.comments.for.method");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_IN_COMMENTS_FOR_CLASS, "rename.search.in.comments.for.class");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE, "rename.search.in.comments.for.package");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE, "rename.search.in.comments.for.variable");

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_FOR_TEXT_FOR_FIELD, "rename.search.for.text.for.field");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_FOR_TEXT_FOR_METHOD, "rename.search.for.text.for.method");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_FOR_TEXT_FOR_CLASS, "rename.search.for.text.for.class");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE, "rename.search.for.text.for.package");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE, "rename.search.for.text.for.variable");

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_INHERITORS, "rename.auto.inheritors");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_OVERLOADS, "rename.auto.overloads");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_TESTS, "rename.auto.tests");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_VARIABLES, "rename.auto.variables");

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INTRODUCE_LOCAL_CREATE_FINALS, "introduce.local.create.finals");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INTRODUCE_LOCAL_CREATE_VAR_TYPE, "introduce.local.use.var");


    addBoolIfDiffers(result, settings, defaultSettings, s -> s.MOVE_SEARCH_IN_COMMENTS, "move.search.in.comments");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.MOVE_SEARCH_FOR_TEXT, "move.search.for.text");

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.ENCAPSULATE_FIELDS_USE_ACCESSORS_WHEN_ACCESSIBLE, "encapsulate.fields.use.accessors");

    addMetricIfDiffers(result, settings, defaultSettings, 
                       s -> getReplaceGettersOption(settings.INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS), 
                       javadoc -> new MetricEvent("introduce.parameter.replace.fields.with.getters", new FeatureUsageData().addData("replace.fields.with.getters", javadoc)));

    addJavadoc(result, settings, defaultSettings, "extract.interface.javadoc", settings.EXTRACT_INTERFACE_JAVADOC);
    addJavadoc(result, settings, defaultSettings, "extract.superclass.javadoc", settings.EXTRACT_SUPERCLASS_JAVADOC);
    addJavadoc(result, settings, defaultSettings, "pull.up.members.javadoc", settings.PULL_UP_MEMBERS_JAVADOC);

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INTRODUCE_PARAMETER_DELETE_LOCAL_VARIABLE, "introduce.parameter.delete.local");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INTRODUCE_PARAMETER_USE_INITIALIZER, "introduce.parameter.use.initializer");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INTRODUCE_PARAMETER_CREATE_FINALS, "introduce.parameter.create.finals");

    addMetricIfDiffers(result, settings, defaultSettings, 
                       s -> getVisibility(settings.INTRODUCE_FIELD_VISIBILITY), 
                       javadoc -> new MetricEvent("introduce.field.visibility", new FeatureUsageData().addData("visibility", javadoc)));
    addMetricIfDiffers(result, settings, defaultSettings, 
                       s -> getVisibility(settings.INTRODUCE_CONSTANT_VISIBILITY), 
                       javadoc -> new MetricEvent("introduce.constant.visibility", new FeatureUsageData().addData("visibility", javadoc)));
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INTRODUCE_CONSTANT_REPLACE_ALL, "introduce.constant.replace.all");

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INLINE_METHOD_THIS, "inline.method.this.only.choice");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INLINE_METHOD_KEEP, "inline.method.all.and.keep.choice");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INLINE_SUPER_CLASS_THIS, "inline.super.class.this.only.choice");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INLINE_FIELD_THIS, "inline.field.this.only.choice");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INLINE_FIELD_KEEP, "inline.field.all.and.keep.choice");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INLINE_LOCAL_THIS, "inline.local.this.only.choice");

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INHERITANCE_TO_DELEGATION_DELEGATE_OTHER, "inheritance.to.delegation.delegate.other");

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INLINE_CLASS_SEARCH_IN_COMMENTS, "inline.class.search.in.comments");
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INLINE_CLASS_SEARCH_IN_NON_JAVA, "inline.class.search.in.non.java");

    return result;
  }

  private static void addJavadoc(Set<MetricEvent> result,
                                 JavaRefactoringSettings settings,
                                 JavaRefactoringSettings defaultSettings,
                                 String eventId, int javadocOption) {
    addMetricIfDiffers(result, settings, defaultSettings, 
                       s -> getJavadocOption(javadocOption), 
                       javadoc -> new MetricEvent(eventId, new FeatureUsageData().addData("javadoc", javadoc)));
  }

  private static String getVisibility(String visibility) {
    return PsiModifier.PUBLIC.equals(visibility) ||
           PsiModifier.PRIVATE.equals(visibility) ||
           PsiModifier.PROTECTED.equals(visibility) ||
           PsiModifier.PACKAGE_LOCAL.equals(visibility) ||
           VisibilityUtil.ESCALATE_VISIBILITY.equals(visibility) ? visibility : "unknown";
  }
  
  private static String getJavadocOption(int javadoc) {
    switch (javadoc) {
      case DocCommentPolicy.ASIS:
        return "as is";
      case DocCommentPolicy.COPY:
        return "copy";
      case DocCommentPolicy.MOVE:
        return "move";
    }
    return "unknown";
  }

  private static String getReplaceGettersOption(int getters) {
    switch (getters) {
      case IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE:
        return "none";
      case IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE:
        return "inaccessible";
      case IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL:
        return "all";
    }
    return "unknown";
  }
}
