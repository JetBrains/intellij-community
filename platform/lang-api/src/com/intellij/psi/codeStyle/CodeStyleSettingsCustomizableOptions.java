// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.application.ApplicationBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public final class CodeStyleSettingsCustomizableOptions {
  private CodeStyleSettingsCustomizableOptions() {
  }

  public static final Supplier<@Nls String> SPACES_AROUND_OPERATORS = ApplicationBundle.messagePointer("group.spaces.around.operators");
  public static final Supplier<@Nls String> SPACES_BEFORE_PARENTHESES = ApplicationBundle.messagePointer("group.spaces.before.parentheses");
  public static final Supplier<@Nls String> SPACES_BEFORE_LEFT_BRACE = ApplicationBundle.messagePointer("group.spaces.before.left.brace");
  public static final Supplier<@Nls String> SPACES_BEFORE_KEYWORD = ApplicationBundle.messagePointer("group.spaces.after.right.brace");
  public static final Supplier<@Nls String> SPACES_WITHIN = ApplicationBundle.messagePointer("group.spaces.within");
  public static final Supplier<@Nls String> SPACES_IN_TERNARY_OPERATOR =
    ApplicationBundle.messagePointer("group.spaces.in.ternary.operator");
  public static final Supplier<@Nls String> SPACES_WITHIN_TYPE_ARGUMENTS =
    ApplicationBundle.messagePointer("group.spaces.in.type.arguments");
  public static final Supplier<@Nls String> SPACES_IN_TYPE_ARGUMENTS =
    ApplicationBundle.messagePointer("group.spaces.in.type.arguments.block");
  public static final Supplier<@Nls String> SPACES_IN_TYPE_PARAMETERS =
    ApplicationBundle.messagePointer("group.spaces.in.type.parameters.block");
  public static final Supplier<@Nls String> SPACES_OTHER = ApplicationBundle.messagePointer("group.spaces.other");

  public static final Supplier<@Nls String> BLANK_LINES_KEEP = ApplicationBundle.messagePointer("title.keep.blank.lines");
  public static final Supplier<@Nls String> BLANK_LINES = ApplicationBundle.messagePointer("title.minimum.blank.lines");

  public static final Supplier<@Nls String> WRAPPING_KEEP = ApplicationBundle.messagePointer("wrapping.keep.when.reformatting");
  public static final Supplier<@Nls String> WRAPPING_BRACES = ApplicationBundle.messagePointer("wrapping.brace.placement");
  public static final Supplier<@Nls String> WRAPPING_COMMENTS = ApplicationBundle.messagePointer("wrapping.comments");
  public static final Supplier<@Nls String> WRAPPING_METHOD_PARAMETERS = ApplicationBundle.messagePointer("wrapping.method.parameters");
  public static final Supplier<@Nls String> WRAPPING_METHOD_PARENTHESES = ApplicationBundle.messagePointer("wrapping.method.parentheses");
  public static final Supplier<@Nls String> WRAPPING_METHOD_ARGUMENTS_WRAPPING =
    ApplicationBundle.messagePointer("wrapping.method.arguments");
  public static final Supplier<@Nls String> WRAPPING_CALL_CHAIN = ApplicationBundle.messagePointer("wrapping.chained.method.calls");
  public static final Supplier<@Nls String> WRAPPING_IF_STATEMENT = ApplicationBundle.messagePointer("wrapping.if.statement");
  public static final Supplier<@Nls String> WRAPPING_FOR_STATEMENT = ApplicationBundle.messagePointer("wrapping.for.statement");
  public static final Supplier<@Nls String> WRAPPING_WHILE_STATEMENT = ApplicationBundle.messagePointer("wrapping.while.statement");
  public static final Supplier<@Nls String> WRAPPING_DOWHILE_STATEMENT = ApplicationBundle.messagePointer("wrapping.dowhile.statement");
  public static final Supplier<@Nls String> WRAPPING_SWITCH_STATEMENT = ApplicationBundle.messagePointer("wrapping.switch.statement");
  public static final Supplier<@Nls String> WRAPPING_TRY_STATEMENT = ApplicationBundle.messagePointer("wrapping.try.statement");
  public static final Supplier<@Nls String> WRAPPING_TRY_RESOURCE_LIST = ApplicationBundle.messagePointer("wrapping.try.resources");
  public static final Supplier<@Nls String> WRAPPING_BINARY_OPERATION = ApplicationBundle.messagePointer("wrapping.binary.operations");
  public static final Supplier<@Nls String> WRAPPING_EXTENDS_LIST = ApplicationBundle.messagePointer("wrapping.extends.implements.list");
  public static final Supplier<@Nls String> WRAPPING_EXTENDS_KEYWORD =
    ApplicationBundle.messagePointer("wrapping.extends.implements.keyword");
  public static final Supplier<@Nls String> WRAPPING_THROWS_LIST = ApplicationBundle.messagePointer("wrapping.throws.list");
  public static final Supplier<@Nls String> WRAPPING_THROWS_KEYWORD = ApplicationBundle.messagePointer("wrapping.throws.keyword");
  public static final Supplier<@Nls String> WRAPPING_TERNARY_OPERATION = ApplicationBundle.messagePointer("wrapping.ternary.operation");
  public static final Supplier<@Nls String> WRAPPING_ASSIGNMENT = ApplicationBundle.messagePointer("wrapping.assignment.statement");
  public static final Supplier<@Nls String> WRAPPING_FIELDS_VARIABLES_GROUPS =
    ApplicationBundle.messagePointer("checkbox.align.multiline.fields.groups");
  public static final Supplier<@Nls String> WRAPPING_ARRAY_INITIALIZER = ApplicationBundle.messagePointer("wrapping.array.initializer");
  public static final Supplier<@Nls String> WRAPPING_MODIFIER_LIST = ApplicationBundle.messagePointer("wrapping.modifier.list");
  public static final Supplier<@Nls String> WRAPPING_ASSERT_STATEMENT = ApplicationBundle.messagePointer("wrapping.assert.statement");


  public static @NotNull @Nls String @NotNull [] getWrapOptions() {
    return new String[]{
      ApplicationBundle.message("wrapping.do.not.wrap"),
      ApplicationBundle.message("wrapping.wrap.if.long"),
      ApplicationBundle.message("wrapping.chop.down.if.long"),
      ApplicationBundle.message("wrapping.wrap.always")
    };
  }

  public static @NotNull @Nls String @NotNull [] getWrapOptionsForSingleton() {
    return new String[]{
      ApplicationBundle.message("wrapping.do.not.wrap"),
      ApplicationBundle.message("wrapping.wrap.if.long"),
      ApplicationBundle.message("wrapping.wrap.always")
    };
  }

  public static String[] getBraceOptions() {
    return new String[]{
      ApplicationBundle.message("wrapping.force.braces.do.not.force"),
      ApplicationBundle.message("wrapping.force.braces.when.multiline"),
      ApplicationBundle.message("wrapping.force.braces.always")
    };
  }

  public static String[] getBraceReplacementOptions() {
    return new String[]{
      ApplicationBundle.message("wrapping.brace.placement.end.of.line"),
      ApplicationBundle.message("wrapping.brace.placement.next.line.if.wrapped"),
      ApplicationBundle.message("wrapping.brace.placement.next.line"),
      ApplicationBundle.message("wrapping.brace.placement.next.line.shifted"),
      ApplicationBundle.message("wrapping.brace.placement.next.line.each.shifted")
    };
  }

  public static String[] getWrapOnTypingOptions() {
    return new String[]{
      ApplicationBundle.message("wrapping.wrap.on.typing.no.wrap"),
      ApplicationBundle.message("wrapping.wrap.on.typing.wrap"),
      ApplicationBundle.message("wrapping.wrap.on.typing.default")
    };
  }
}
