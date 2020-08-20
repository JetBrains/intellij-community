// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.util.LocaleSensitiveApplicationCacheService;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class CodeStyleSettingsCustomizableOptions {
  private CodeStyleSettingsCustomizableOptions() {
  }

  public final @Nls String SPACES_AROUND_OPERATORS = ApplicationBundle.message("group.spaces.around.operators");
  public final @Nls String SPACES_BEFORE_PARENTHESES = ApplicationBundle.message("group.spaces.before.parentheses");
  public final @Nls String SPACES_BEFORE_LEFT_BRACE = ApplicationBundle.message("group.spaces.before.left.brace");
  public final @Nls String SPACES_BEFORE_KEYWORD = ApplicationBundle.message("group.spaces.after.right.brace");
  public final @Nls String SPACES_WITHIN = ApplicationBundle.message("group.spaces.within");
  public final @Nls String SPACES_IN_TERNARY_OPERATOR = ApplicationBundle.message("group.spaces.in.ternary.operator");
  public final @Nls String SPACES_WITHIN_TYPE_ARGUMENTS = ApplicationBundle.message("group.spaces.in.type.arguments");
  public final @Nls String SPACES_IN_TYPE_ARGUMENTS = ApplicationBundle.message("group.spaces.in.type.arguments.block");
  public final @Nls String SPACES_IN_TYPE_PARAMETERS = ApplicationBundle.message("group.spaces.in.type.parameters.block");
  public final @Nls String SPACES_OTHER = ApplicationBundle.message("group.spaces.other");

  public final @Nls String BLANK_LINES_KEEP = ApplicationBundle.message("title.keep.blank.lines");
  public final @Nls String BLANK_LINES = ApplicationBundle.message("title.minimum.blank.lines");

  public final @Nls String WRAPPING_KEEP = ApplicationBundle.message("wrapping.keep.when.reformatting");
  public final @Nls String WRAPPING_BRACES = ApplicationBundle.message("wrapping.brace.placement");
  public final @Nls String WRAPPING_COMMENTS = ApplicationBundle.message("wrapping.comments");
  public final @Nls String WRAPPING_METHOD_PARAMETERS = ApplicationBundle.message("wrapping.method.parameters");
  public final @Nls String WRAPPING_METHOD_PARENTHESES = ApplicationBundle.message("wrapping.method.parentheses");
  public final @Nls String WRAPPING_METHOD_ARGUMENTS_WRAPPING = ApplicationBundle.message("wrapping.method.arguments");
  public final @Nls String WRAPPING_CALL_CHAIN = ApplicationBundle.message("wrapping.chained.method.calls");
  public final @Nls String WRAPPING_IF_STATEMENT = ApplicationBundle.message("wrapping.if.statement");
  public final @Nls String WRAPPING_FOR_STATEMENT = ApplicationBundle.message("wrapping.for.statement");
  public final @Nls String WRAPPING_WHILE_STATEMENT = ApplicationBundle.message("wrapping.while.statement");
  public final @Nls String WRAPPING_DOWHILE_STATEMENT = ApplicationBundle.message("wrapping.dowhile.statement");
  public final @Nls String WRAPPING_SWITCH_STATEMENT = ApplicationBundle.message("wrapping.switch.statement");
  public final @Nls String WRAPPING_TRY_STATEMENT = ApplicationBundle.message("wrapping.try.statement");
  public final @Nls String WRAPPING_TRY_RESOURCE_LIST = ApplicationBundle.message("wrapping.try.resources");
  public final @Nls String WRAPPING_BINARY_OPERATION = ApplicationBundle.message("wrapping.binary.operations");
  public final @Nls String WRAPPING_EXTENDS_LIST = ApplicationBundle.message("wrapping.extends.implements.list");
  public final @Nls String WRAPPING_EXTENDS_KEYWORD = ApplicationBundle.message("wrapping.extends.implements.keyword");
  public final @Nls String WRAPPING_THROWS_LIST = ApplicationBundle.message("wrapping.throws.list");
  public final @Nls String WRAPPING_THROWS_KEYWORD = ApplicationBundle.message("wrapping.throws.keyword");
  public final @Nls String WRAPPING_TERNARY_OPERATION = ApplicationBundle.message("wrapping.ternary.operation");
  public final @Nls String WRAPPING_ASSIGNMENT = ApplicationBundle.message("wrapping.assignment.statement");
  public final @Nls String WRAPPING_FIELDS_VARIABLES_GROUPS = ApplicationBundle.message("checkbox.align.multiline.fields.groups");
  public final @Nls String WRAPPING_ARRAY_INITIALIZER = ApplicationBundle.message("wrapping.array.initializer");
  public final @Nls String WRAPPING_MODIFIER_LIST = ApplicationBundle.message("wrapping.modifier.list");
  public final @Nls String WRAPPING_ASSERT_STATEMENT = ApplicationBundle.message("wrapping.assert.statement");

  public final @Nls String[] WRAP_OPTIONS = {
    ApplicationBundle.message("wrapping.do.not.wrap"),
    ApplicationBundle.message("wrapping.wrap.if.long"),
    ApplicationBundle.message("wrapping.chop.down.if.long"),
    ApplicationBundle.message("wrapping.wrap.always")
  };

  public final @Nls String[] WRAP_OPTIONS_FOR_SINGLETON = {
    ApplicationBundle.message("wrapping.do.not.wrap"),
    ApplicationBundle.message("wrapping.wrap.if.long"),
    ApplicationBundle.message("wrapping.wrap.always")
  };

  public final @Nls String[] BRACE_OPTIONS = {
    ApplicationBundle.message("wrapping.force.braces.do.not.force"),
    ApplicationBundle.message("wrapping.force.braces.when.multiline"),
    ApplicationBundle.message("wrapping.force.braces.always")
  };

  public final @Nls String[] BRACE_PLACEMENT_OPTIONS = {
    ApplicationBundle.message("wrapping.brace.placement.end.of.line"),
    ApplicationBundle.message("wrapping.brace.placement.next.line.if.wrapped"),
    ApplicationBundle.message("wrapping.brace.placement.next.line"),
    ApplicationBundle.message("wrapping.brace.placement.next.line.shifted"),
    ApplicationBundle.message("wrapping.brace.placement.next.line.each.shifted")
  };

  public final @Nls String[] WRAP_ON_TYPING_OPTIONS = {
    ApplicationBundle.message("wrapping.wrap.on.typing.no.wrap"),
    ApplicationBundle.message("wrapping.wrap.on.typing.wrap"),
    ApplicationBundle.message("wrapping.wrap.on.typing.default")
  };

  public static @NotNull CodeStyleSettingsCustomizableOptions getInstance() {
    return LocaleSensitiveApplicationCacheService.getInstance().getData(CodeStyleSettingsCustomizableOptions.class,
                                                                        CodeStyleSettingsCustomizableOptions::new);
  }
}
