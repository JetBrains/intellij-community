// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.events.StringEventField;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.intellij.internal.statistic.beans.MetricEventUtilKt.addBoolIfDiffers;
import static com.intellij.internal.statistic.beans.MetricEventUtilKt.addMetricIfDiffers;

@NonNls
public final class JavaRefactoringUsageCollector extends ApplicationUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("java.refactoring.settings", 7);
  private static final VarargEventId RENAME_SEARCH_IN_COMMENTS_FOR_FIELD =
    GROUP.registerVarargEvent("rename.search.in.comments.for.field", EventFields.Enabled);
  private static final VarargEventId RENAME_SEARCH_IN_COMMENTS_FOR_METHOD =
    GROUP.registerVarargEvent("rename.search.in.comments.for.method", EventFields.Enabled);
  private static final VarargEventId RENAME_SEARCH_IN_COMMENTS_FOR_CLASS =
    GROUP.registerVarargEvent("rename.search.in.comments.for.class", EventFields.Enabled);
  private static final VarargEventId
    RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE = GROUP.registerVarargEvent("rename.search.in.comments.for.package", EventFields.Enabled);
  private static final VarargEventId
    RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = GROUP.registerVarargEvent("rename.search.in.comments.for.variable", EventFields.Enabled);
  private static final VarargEventId
    RENAME_SEARCH_FOR_TEXT_FOR_FIELD = GROUP.registerVarargEvent("rename.search.for.text.for.field", EventFields.Enabled);
  private static final VarargEventId
    RENAME_SEARCH_FOR_TEXT_FOR_METHOD = GROUP.registerVarargEvent("rename.search.for.text.for.method", EventFields.Enabled);
  private static final VarargEventId
    RENAME_SEARCH_FOR_TEXT_FOR_CLASS = GROUP.registerVarargEvent("rename.search.for.text.for.class", EventFields.Enabled);
  private static final VarargEventId
    RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE = GROUP.registerVarargEvent("rename.search.for.text.for.package", EventFields.Enabled);
  private static final VarargEventId
    RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE = GROUP.registerVarargEvent("rename.search.for.text.for.variable", EventFields.Enabled);
  private static final VarargEventId RENAME_AUTO_INHERITORS = GROUP.registerVarargEvent("rename.auto.inheritors", EventFields.Enabled);
  private static final VarargEventId RENAME_AUTO_OVERLOADS = GROUP.registerVarargEvent("rename.auto.overloads", EventFields.Enabled);
  private static final VarargEventId RENAME_AUTO_TESTS = GROUP.registerVarargEvent("rename.auto.tests", EventFields.Enabled);
  private static final VarargEventId RENAME_AUTO_VARIABLES = GROUP.registerVarargEvent("rename.auto.variables", EventFields.Enabled);
  private static final VarargEventId
    INTRODUCE_LOCAL_CREATE_FINALS = GROUP.registerVarargEvent("introduce.local.create.finals", EventFields.Enabled);
  private static final VarargEventId INTRODUCE_LOCAL_USE_VAR = GROUP.registerVarargEvent("introduce.local.use.var", EventFields.Enabled);
  private static final VarargEventId MOVE_SEARCH_IN_COMMENTS = GROUP.registerVarargEvent("move.search.in.comments", EventFields.Enabled);
  private static final VarargEventId MOVE_SEARCH_FOR_TEXT = GROUP.registerVarargEvent("move.search.for.text", EventFields.Enabled);
  private static final VarargEventId
    ENCAPSULATE_FIELDS_USE_ACCESSORS = GROUP.registerVarargEvent("encapsulate.fields.use.accessors", EventFields.Enabled);
  private static final VarargEventId
    INTRODUCE_PARAMETER_DELETE_LOCAL = GROUP.registerVarargEvent("introduce.parameter.delete.local", EventFields.Enabled);
  private static final VarargEventId
    INTRODUCE_PARAMETER_USE_INITIALIZER = GROUP.registerVarargEvent("introduce.parameter.use.initializer", EventFields.Enabled);
  private static final VarargEventId
    INTRODUCE_PARAMETER_CREATE_FINALS = GROUP.registerVarargEvent("introduce.parameter.create.finals", EventFields.Enabled);
  private static final VarargEventId
    INTRODUCE_CONSTANT_REPLACE_ALL = GROUP.registerVarargEvent("introduce.constant.replace.all", EventFields.Enabled);
  private static final VarargEventId
    INLINE_METHOD_THIS_ONLY_CHOICE = GROUP.registerVarargEvent("inline.method.this.only.choice", EventFields.Enabled);
  private static final VarargEventId
    INLINE_METHOD_ALL_AND_KEEP_CHOICE = GROUP.registerVarargEvent("inline.method.all.and.keep.choice", EventFields.Enabled);
  private static final VarargEventId
    INLINE_SUPER_CLASS_THIS_ONLY_CHOICE = GROUP.registerVarargEvent("inline.super.class.this.only.choice", EventFields.Enabled);
  private static final VarargEventId
    INLINE_FIELD_THIS_ONLY_CHOICE = GROUP.registerVarargEvent("inline.field.this.only.choice", EventFields.Enabled);
  private static final VarargEventId
    INLINE_FIELD_ALL_AND_KEEP_CHOICE = GROUP.registerVarargEvent("inline.field.all.and.keep.choice", EventFields.Enabled);
  private static final VarargEventId
    INLINE_LOCAL_THIS_ONLY_CHOICE = GROUP.registerVarargEvent("inline.local.this.only.choice", EventFields.Enabled);
  private static final VarargEventId
    INHERITANCE_TO_DELEGATION_DELEGATE_OTHER = GROUP.registerVarargEvent("inheritance.to.delegation.delegate.other", EventFields.Enabled);
  private static final VarargEventId
    INLINE_CLASS_SEARCH_IN_COMMENTS = GROUP.registerVarargEvent("inline.class.search.in.comments", EventFields.Enabled);
  private static final VarargEventId
    INLINE_CLASS_SEARCH_IN_NON_JAVA = GROUP.registerVarargEvent("inline.class.search.in.non.java", EventFields.Enabled);
  private static final EventId1<String>
    INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS = GROUP.registerEvent("introduce.parameter.replace.fields.with.getters",
                                                                          EventFields.String("replace_fields_with_getters",
                                                                                             List.of("none", "inaccessible", "all",
                                                                                                     "unknown")));
  private static final StringEventField VISIBILITY =
    EventFields.String("visibility", List.of("public", "protected", "packageLocal", "private", "EscalateVisible", "unknown"));
  private static final VarargEventId INTRODUCE_FIELD_VISIBILITY = GROUP.registerVarargEvent("introduce.field.visibility", VISIBILITY);
  private static final VarargEventId INTRODUCE_CONSTANT_VISIBILITY = GROUP.registerVarargEvent("introduce.constant.visibility", VISIBILITY);
  private static final StringEventField JAVADOC = EventFields.String("javadoc", List.of("as_is", "copy", "move", "unknown"));
  private static final VarargEventId EXTRACT_INTERFACE_JAVADOC = GROUP.registerVarargEvent("extract.interface.javadoc", JAVADOC);
  private static final VarargEventId EXTRACT_SUPERCLASS_JAVADOC = GROUP.registerVarargEvent("extract.superclass.javadoc", JAVADOC);
  private static final VarargEventId PULL_UP_MEMBERS_JAVADOC = GROUP.registerVarargEvent("pull.up.members.javadoc", JAVADOC);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics() {
    Set<MetricEvent> result = new HashSet<>();
    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    JavaRefactoringSettings defaultSettings = new JavaRefactoringSettings();

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_IN_COMMENTS_FOR_FIELD, RENAME_SEARCH_IN_COMMENTS_FOR_FIELD);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD, RENAME_SEARCH_IN_COMMENTS_FOR_METHOD);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_IN_COMMENTS_FOR_CLASS, RENAME_SEARCH_IN_COMMENTS_FOR_CLASS);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE,
                     RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE,
                     RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE);

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_FOR_TEXT_FOR_FIELD, RENAME_SEARCH_FOR_TEXT_FOR_FIELD);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_FOR_TEXT_FOR_METHOD, RENAME_SEARCH_FOR_TEXT_FOR_METHOD);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_FOR_TEXT_FOR_CLASS, RENAME_SEARCH_FOR_TEXT_FOR_CLASS);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE, RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE, RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE);

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_INHERITORS, RENAME_AUTO_INHERITORS);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_OVERLOADS, RENAME_AUTO_OVERLOADS);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_TESTS, RENAME_AUTO_TESTS);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.RENAME_VARIABLES, RENAME_AUTO_VARIABLES);

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INTRODUCE_LOCAL_CREATE_FINALS, INTRODUCE_LOCAL_CREATE_FINALS);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INTRODUCE_LOCAL_CREATE_VAR_TYPE, INTRODUCE_LOCAL_USE_VAR);


    addBoolIfDiffers(result, settings, defaultSettings, s -> s.MOVE_SEARCH_IN_COMMENTS, MOVE_SEARCH_IN_COMMENTS);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.MOVE_SEARCH_FOR_TEXT, MOVE_SEARCH_FOR_TEXT);

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.ENCAPSULATE_FIELDS_USE_ACCESSORS_WHEN_ACCESSIBLE,
                     ENCAPSULATE_FIELDS_USE_ACCESSORS);

    addMetricIfDiffers(result, settings, defaultSettings,
                       s -> getReplaceGettersOption(s.INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS),
                       javadoc -> INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS.metric(javadoc));

    addJavadoc(result, settings, defaultSettings, EXTRACT_INTERFACE_JAVADOC, s -> s.EXTRACT_INTERFACE_JAVADOC);
    addJavadoc(result, settings, defaultSettings, EXTRACT_SUPERCLASS_JAVADOC, s -> s.EXTRACT_SUPERCLASS_JAVADOC);
    addJavadoc(result, settings, defaultSettings, PULL_UP_MEMBERS_JAVADOC, s -> s.PULL_UP_MEMBERS_JAVADOC);

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INTRODUCE_PARAMETER_DELETE_LOCAL_VARIABLE, INTRODUCE_PARAMETER_DELETE_LOCAL);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INTRODUCE_PARAMETER_USE_INITIALIZER, INTRODUCE_PARAMETER_USE_INITIALIZER);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INTRODUCE_PARAMETER_CREATE_FINALS, INTRODUCE_PARAMETER_CREATE_FINALS);

    addMetricIfDiffers(result, settings, defaultSettings,
                       s -> getVisibility(s.INTRODUCE_FIELD_VISIBILITY),
                       javadoc -> INTRODUCE_FIELD_VISIBILITY.metric(VISIBILITY.with(javadoc)));
    addMetricIfDiffers(result, settings, defaultSettings,
                       s -> getVisibility(s.INTRODUCE_CONSTANT_VISIBILITY),
                       javadoc -> INTRODUCE_CONSTANT_VISIBILITY.metric(VISIBILITY.with(javadoc)));
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INTRODUCE_CONSTANT_REPLACE_ALL, INTRODUCE_CONSTANT_REPLACE_ALL);

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INLINE_METHOD_THIS, INLINE_METHOD_THIS_ONLY_CHOICE);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INLINE_METHOD_KEEP, INLINE_METHOD_ALL_AND_KEEP_CHOICE);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INLINE_SUPER_CLASS_THIS, INLINE_SUPER_CLASS_THIS_ONLY_CHOICE);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INLINE_FIELD_THIS, INLINE_FIELD_THIS_ONLY_CHOICE);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INLINE_FIELD_KEEP, INLINE_FIELD_ALL_AND_KEEP_CHOICE);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INLINE_LOCAL_THIS, INLINE_LOCAL_THIS_ONLY_CHOICE);

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INHERITANCE_TO_DELEGATION_DELEGATE_OTHER,
                     INHERITANCE_TO_DELEGATION_DELEGATE_OTHER);

    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INLINE_CLASS_SEARCH_IN_COMMENTS, INLINE_CLASS_SEARCH_IN_COMMENTS);
    addBoolIfDiffers(result, settings, defaultSettings, s -> s.INLINE_CLASS_SEARCH_IN_NON_JAVA, INLINE_CLASS_SEARCH_IN_NON_JAVA);

    return result;
  }

  private static void addJavadoc(Set<MetricEvent> result,
                                 JavaRefactoringSettings settings,
                                 JavaRefactoringSettings defaultSettings,
                                 VarargEventId eventId, 
                                 Function<JavaRefactoringSettings, Integer> javadocOption) {
    addMetricIfDiffers(result, settings, defaultSettings,
                       s -> getJavadocOption(javadocOption.apply(s)),
                       javadoc -> eventId.metric(JAVADOC.with(javadoc)));
  }

  private static String getVisibility(String visibility) {
    return PsiModifier.PUBLIC.equals(visibility) ||
           PsiModifier.PRIVATE.equals(visibility) ||
           PsiModifier.PROTECTED.equals(visibility) ||
           PsiModifier.PACKAGE_LOCAL.equals(visibility) ||
           VisibilityUtil.ESCALATE_VISIBILITY.equals(visibility) ? visibility : "unknown";
  }
  
  private static String getJavadocOption(int javadoc) {
    return switch (javadoc) {
      case DocCommentPolicy.ASIS -> "as is";
      case DocCommentPolicy.COPY -> "copy";
      case DocCommentPolicy.MOVE -> "move";
      default -> "unknown";
    };
  }

  private static String getReplaceGettersOption(int getters) {
    return switch (getters) {
      case IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE -> "none";
      case IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE -> "inaccessible";
      case IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL -> "all";
      default -> "unknown";
    };
  }
}
