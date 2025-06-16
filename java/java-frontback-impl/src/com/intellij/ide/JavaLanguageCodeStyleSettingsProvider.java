// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.application.options.*;
import com.intellij.application.options.codeStyle.properties.CodeStyleFieldAccessor;
import com.intellij.application.options.codeStyle.properties.CodeStylePropertiesUtil;
import com.intellij.application.options.codeStyle.properties.CodeStylePropertyAccessor;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.frontback.impl.JavaFrontbackBundle;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizableOptions.getInstance;

public final class JavaLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @Override
  public @NotNull CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings settings, @NotNull CodeStyleSettings modelSettings) {
    return new CodeStyleAbstractConfigurable(settings, modelSettings, JavaLanguage.INSTANCE.getDisplayName()) {
      @Override
      protected @NotNull CodeStyleAbstractPanel createPanel(final @NotNull CodeStyleSettings settings) {
        return new JavaCodeStyleMainPanel(getCurrentSettings(), settings);
      }
      @Override
      public String getHelpTopic() {
        return "reference.settingsdialog.codestyle.java";
      }
    };
  }

  @Override
  public @Nullable CustomCodeStyleSettings createCustomSettings(@NotNull CodeStyleSettings settings) {
    return new JavaCodeStyleSettings(settings);
  }

  @Override
  public @NotNull Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.SPACING_SETTINGS) return SPACING_SAMPLE;
    if (settingsType == SettingsType.BLANK_LINES_SETTINGS) return BLANK_LINE_SAMPLE;
    if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) return WRAPPING_CODE_SAMPLE;

    return GENERAL_CODE_SAMPLE;
  }

  @Override
  public int getRightMargin(@NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) return 37;
    return super.getRightMargin(settingsType);
  }

  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer, @NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.SPACING_SETTINGS) {
      consumer.showAllStandardOptions();
      consumer.showCustomOption(JavaCodeStyleSettings.class, "SPACES_WITHIN_ANGLE_BRACKETS",
                                JavaFrontbackBundle.message("code.style.settings.angle.spacing.brackets"), getInstance().SPACES_WITHIN);
      consumer.showCustomOption(JavaCodeStyleSettings.class, "SPACE_AROUND_ANNOTATION_EQ",
                                JavaFrontbackBundle.message("checkbox.spaces.around.annotation.eq"), getInstance().SPACES_OTHER);
      consumer.showCustomOption(JavaCodeStyleSettings.class, "SPACE_WITHIN_RECORD_HEADER",
                                JavaFrontbackBundle.message("checkbox.spaces.record.header"), getInstance().SPACES_WITHIN);
      consumer.showCustomOption(JavaCodeStyleSettings.class, "SPACE_WITHIN_DECONSTRUCTION_LIST",
                                JavaFrontbackBundle.message("checkbox.spaces.within.deconstruction.list"), getInstance().SPACES_WITHIN);
      consumer.showCustomOption(JavaCodeStyleSettings.class, "SPACES_INSIDE_BLOCK_BRACES_WHEN_BODY_IS_PRESENT",
                                JavaFrontbackBundle.message("checkbox.spaces.inside.block.braces.when.body.is.present"), getInstance().SPACES_WITHIN);

      String groupName = getInstance().SPACES_IN_TYPE_ARGUMENTS;
      consumer.moveStandardOption("SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS", groupName);
      consumer.showCustomOption(JavaCodeStyleSettings.class, "SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENT",
                                JavaFrontbackBundle.message("code.style.settings.spacing.after.closing.angle.bracket"), groupName);

      groupName = getInstance().SPACES_IN_TYPE_PARAMETERS;
      consumer.showCustomOption(JavaCodeStyleSettings.class, "SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETER",
                                ApplicationBundle.message("checkbox.spaces.before.opening.angle.bracket"), groupName);
      consumer.showCustomOption(JavaCodeStyleSettings.class, "SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS",
                                JavaFrontbackBundle.message("code.style.settings.spacing.around.type.bounds"), groupName);

      groupName = getInstance().SPACES_OTHER;
      consumer.showCustomOption(JavaCodeStyleSettings.class, "SPACE_BEFORE_COLON_IN_FOREACH", JavaFrontbackBundle.message(
        "checkbox.spaces.before.colon.in.foreach"), groupName);
      consumer.showCustomOption(JavaCodeStyleSettings.class, "SPACE_INSIDE_ONE_LINE_ENUM_BRACES", JavaFrontbackBundle.message(
        "checkbox.spaces.inside.one.line.enum"), groupName);


      consumer.showCustomOption(JavaCodeStyleSettings.class, "SPACE_BEFORE_DECONSTRUCTION_LIST", JavaFrontbackBundle.message(
        "checkbox.spaces.before.deconstruction.list"), getInstance().SPACES_BEFORE_PARENTHESES);
    }
    else if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
      consumer.showStandardOptions("RIGHT_MARGIN",
                                   "WRAP_ON_TYPING",
                                   "KEEP_CONTROL_STATEMENT_IN_ONE_LINE",
                                   "KEEP_LINE_BREAKS",
                                   "KEEP_FIRST_COLUMN_COMMENT",
                                   "CALL_PARAMETERS_WRAP",
                                   "PREFER_PARAMETERS_WRAP",
                                   "CALL_PARAMETERS_LPAREN_ON_NEXT_LINE",
                                   "CALL_PARAMETERS_RPAREN_ON_NEXT_LINE",
                                   "METHOD_PARAMETERS_WRAP",
                                   "METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE",
                                   "METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE",
                                   "RESOURCE_LIST_WRAP",
                                   "RESOURCE_LIST_LPAREN_ON_NEXT_LINE",
                                   "RESOURCE_LIST_RPAREN_ON_NEXT_LINE",
                                   "EXTENDS_LIST_WRAP",
                                   "THROWS_LIST_WRAP",
                                   "EXTENDS_KEYWORD_WRAP",
                                   "THROWS_KEYWORD_WRAP",
                                   "METHOD_CALL_CHAIN_WRAP",
                                   "PARENTHESES_EXPRESSION_LPAREN_WRAP",
                                   "PARENTHESES_EXPRESSION_RPAREN_WRAP",
                                   "BINARY_OPERATION_WRAP",
                                   "BINARY_OPERATION_SIGN_ON_NEXT_LINE",
                                   "TERNARY_OPERATION_WRAP",
                                   "TERNARY_OPERATION_SIGNS_ON_NEXT_LINE",
                                   "MODIFIER_LIST_WRAP",
                                   "KEEP_SIMPLE_BLOCKS_IN_ONE_LINE",
                                   "KEEP_SIMPLE_METHODS_IN_ONE_LINE",
                                   "KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE",
                                   "KEEP_SIMPLE_CLASSES_IN_ONE_LINE",
                                   "KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE",
                                   "FOR_STATEMENT_WRAP",
                                   "FOR_STATEMENT_LPAREN_ON_NEXT_LINE",
                                   "FOR_STATEMENT_RPAREN_ON_NEXT_LINE",
                                   "ARRAY_INITIALIZER_WRAP",
                                   "ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE",
                                   "ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE",
                                   "ASSIGNMENT_WRAP",
                                   "PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE",
                                   "LABELED_STATEMENT_WRAP",
                                   "ASSERT_STATEMENT_WRAP",
                                   "ASSERT_STATEMENT_COLON_ON_NEXT_LINE",
                                   "IF_BRACE_FORCE",
                                   "DOWHILE_BRACE_FORCE",
                                   "WHILE_BRACE_FORCE",
                                   "FOR_BRACE_FORCE",
                                   "WRAP_LONG_LINES",
                                   "METHOD_ANNOTATION_WRAP",
                                   "CLASS_ANNOTATION_WRAP",
                                   "FIELD_ANNOTATION_WRAP",
                                   "PARAMETER_ANNOTATION_WRAP",
                                   "VARIABLE_ANNOTATION_WRAP",
                                   "ALIGN_MULTILINE_CHAINED_METHODS",
                                   "ALIGN_MULTILINE_PARAMETERS",
                                   "ALIGN_MULTILINE_PARAMETERS_IN_CALLS",
                                   "ALIGN_MULTILINE_RESOURCES",
                                   "ALIGN_MULTILINE_FOR",
                                   "INDENT_WHEN_CASES",
                                   "ALIGN_MULTILINE_BINARY_OPERATION",
                                   "ALIGN_MULTILINE_ASSIGNMENT",
                                   "ALIGN_MULTILINE_TERNARY_OPERATION",
                                   "ALIGN_MULTILINE_THROWS_LIST",
                                   "ALIGN_THROWS_KEYWORD",
                                   "ALIGN_MULTILINE_EXTENDS_LIST",
                                   "ALIGN_MULTILINE_METHOD_BRACKETS",
                                   "ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION",
                                   "ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION",
                                   "ALIGN_GROUP_FIELD_DECLARATIONS",
                                   "ALIGN_MULTILINE_TEXT_BLOCKS",
                                   "BRACE_STYLE",
                                   "CLASS_BRACE_STYLE",
                                   "METHOD_BRACE_STYLE",
                                   "LAMBDA_BRACE_STYLE",
                                   "USE_FLYING_GEESE_BRACES",
                                   "FLYING_GEESE_BRACES_GAP",
                                   "DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS",
                                   "ELSE_ON_NEW_LINE",
                                   "WHILE_ON_NEW_LINE",
                                   "CATCH_ON_NEW_LINE",
                                   "FINALLY_ON_NEW_LINE",
                                   "SWITCH_EXPRESSIONS_WRAP",
                                   "INDENT_CASE_FROM_SWITCH",
                                   "CASE_STATEMENT_ON_NEW_LINE",
                                   "SPECIAL_ELSE_IF_TREATMENT",
                                   "ENUM_CONSTANTS_WRAP",
                                   "ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS",
                                   "ALIGN_CONSECUTIVE_ASSIGNMENTS",
                                   "ALIGN_SUBSEQUENT_SIMPLE_METHODS",
                                   "WRAP_FIRST_METHOD_IN_CALL_CHAIN",
                                   "BUILDER_METHODS",
                                   "KEEP_BUILDER_METHODS_INDENTS");

      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "WRAP_SEMICOLON_AFTER_CALL_CHAIN",
                                JavaFrontbackBundle.message("wrapping.semicolon.after.call.chain"),
                                getInstance().WRAPPING_CALL_CHAIN);

      consumer.showCustomOption(JavaCodeStyleSettings.class, "ENUM_FIELD_ANNOTATION_WRAP", JavaFrontbackBundle.message("wrapping.annotation.enums"),
                                null, getInstance().WRAP_OPTIONS, CodeStyleSettingsCustomizable.WRAP_VALUES);

      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "ANNOTATION_PARAMETER_WRAP",
                                JavaFrontbackBundle.message("wrapping.annotation.parameters"),
                                null,
                                getInstance().WRAP_OPTIONS, CodeStyleSettingsCustomizable.WRAP_VALUES);

      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "ALIGN_MULTILINE_ANNOTATION_PARAMETERS",
                                ApplicationBundle.message("wrapping.align.when.multiline"),
                                JavaFrontbackBundle.message("wrapping.annotation.parameters"));

      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "NEW_LINE_AFTER_LPAREN_IN_ANNOTATION",
                                ApplicationBundle.message("wrapping.new.line.after.lpar"),
                                JavaFrontbackBundle.message("wrapping.annotation.parameters"));

      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "RPAREN_ON_NEW_LINE_IN_ANNOTATION",
                                ApplicationBundle.message("wrapping.rpar.on.new.line"),
                                JavaFrontbackBundle.message("wrapping.annotation.parameters"));

      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "ALIGN_MULTILINE_TEXT_BLOCKS",
                                ApplicationBundle.message("wrapping.align.when.multiline"),
                                JavaFrontbackBundle.message("wrapping.text.blocks") );

      String fieldAnnotations = ApplicationBundle.message("wrapping.fields.annotation");
      consumer.showCustomOption(JavaCodeStyleSettings.class, "DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION",
                                JavaFrontbackBundle.message("checkbox.do.not.wrap.after.single.annotation"), fieldAnnotations);

      String parameterAnnotationsWrapping = ApplicationBundle.message("wrapping.parameters.annotation");
      consumer.showCustomOption(JavaCodeStyleSettings.class, "DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION_IN_PARAMETER",
                                JavaFrontbackBundle.message("checkbox.do.not.wrap.after.single.annotation"), parameterAnnotationsWrapping);

      consumer.showCustomOption(JavaCodeStyleSettings.class, "NEW_LINE_WHEN_BODY_IS_PRESENTED",
                                JavaFrontbackBundle.message("new.line.when.body.is.presented"),
                                ApplicationBundle.message("wrapping.method.parentheses"));

      // Record components
      String recordComponentsGroup = JavaFrontbackBundle.message("wrapping.record.components");
      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "RECORD_COMPONENTS_WRAP",
                                recordComponentsGroup,
                                null,
                                getInstance().WRAP_OPTIONS, CodeStyleSettingsCustomizable.WRAP_VALUES);
      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "ALIGN_MULTILINE_RECORDS",
                                ApplicationBundle.message("wrapping.align.when.multiline"),
                                recordComponentsGroup);
      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER",
                                ApplicationBundle.message("wrapping.new.line.after.lpar"),
                                recordComponentsGroup);
      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "RPAREN_ON_NEW_LINE_IN_RECORD_HEADER",
                                ApplicationBundle.message("wrapping.rpar.on.new.line"),
                                recordComponentsGroup);
      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "ANNOTATION_NEW_LINE_IN_RECORD_COMPONENT",
                                JavaFrontbackBundle.message("annotations.new.line.record.component"),
                                recordComponentsGroup);

      // Try statement
      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "MULTI_CATCH_TYPES_WRAP",
                                JavaFrontbackBundle.message("wrapping.multi.catch.types"),
                                ApplicationBundle.message("wrapping.try.statement"),
                                getInstance().WRAP_OPTIONS, CodeStyleSettingsCustomizable.WRAP_VALUES);
      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "ALIGN_TYPES_IN_MULTI_CATCH",
                                JavaFrontbackBundle.message("align.types.in.multi.catch"),
                                ApplicationBundle.message("wrapping.try.statement"));

      // Deconstruction patterns
      String deconstructionComponentsGroup = JavaFrontbackBundle.message("wrapping.deconstruction.patterns");
      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "DECONSTRUCTION_LIST_WRAP",
                                deconstructionComponentsGroup,
                                null,
                                getInstance().WRAP_OPTIONS, CodeStyleSettingsCustomizable.WRAP_VALUES);
      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "ALIGN_MULTILINE_DECONSTRUCTION_LIST_COMPONENTS",
                                ApplicationBundle.message("wrapping.align.when.multiline"),
                                deconstructionComponentsGroup);

      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "NEW_LINE_AFTER_LPAREN_IN_DECONSTRUCTION_PATTERN",
                                ApplicationBundle.message("wrapping.new.line.after.lpar"),
                                deconstructionComponentsGroup);
      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "RPAREN_ON_NEW_LINE_IN_DECONSTRUCTION_PATTERN",
                                ApplicationBundle.message("wrapping.rpar.on.new.line"),
                                deconstructionComponentsGroup);

      consumer.renameStandardOption(
        "SWITCH_EXPRESSIONS_WRAP",
        JavaFrontbackBundle.message("wrapping.switch.statement.or.expression")
      );
    }
    else if (settingsType == SettingsType.BLANK_LINES_SETTINGS) {
      consumer.showAllStandardOptions();
      consumer.showCustomOption(JavaCodeStyleSettings.class, "BLANK_LINES_AROUND_INITIALIZER",
                                JavaFrontbackBundle.message("editbox.blanklines.around.initializer"),
                                getInstance().BLANK_LINES);

      consumer.renameStandardOption(
        "BLANK_LINES_AROUND_FIELD_IN_INTERFACE",
        JavaFrontbackBundle.message("editbox.blank.lines.field.in.interface"));

      consumer.renameStandardOption(
        "BLANK_LINES_AROUND_FIELD",
        JavaFrontbackBundle.message("editbox.blank.lines.field.without.annotations"));

      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "BLANK_LINES_AROUND_FIELD_WITH_ANNOTATIONS",
                                JavaFrontbackBundle.message("editbox.blank.lines.field.with.annotations"),
                                getInstance().BLANK_LINES,
                                CodeStyleSettingsCustomizable.OptionAnchor.AFTER,
                                "BLANK_LINES_AROUND_FIELD");

      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "BLANK_LINES_BETWEEN_RECORD_COMPONENTS",
                                JavaFrontbackBundle.message("editbox.blank.lines.record.components"),
                                getInstance().BLANK_LINES);
    }
    else if (settingsType == SettingsType.COMMENTER_SETTINGS) {
      consumer.showStandardOptions(
        "LINE_COMMENT_ADD_SPACE",
        "LINE_COMMENT_ADD_SPACE_ON_REFORMAT",
        "LINE_COMMENT_AT_FIRST_COLUMN",
        "BLOCK_COMMENT_AT_FIRST_COLUMN",
        "BLOCK_COMMENT_ADD_SPACE"
      );
    }
    else if (settingsType == SettingsType.LANGUAGE_SPECIFIC) {
      consumer.showCustomOption(JavaCodeStyleSettings.class, "JD_ALIGN_PARAM_COMMENTS",
                                JavaFrontbackBundle.message("checkbox.align.parameter.descriptions"),
                                JavaDocFormattingPanel.getAlignmentGroup());
      consumer.showCustomOption(JavaCodeStyleSettings.class, "JD_ALIGN_EXCEPTION_COMMENTS",
                                JavaFrontbackBundle.message("checkbox.align.thrown.exception.descriptions"),
                                JavaDocFormattingPanel.getAlignmentGroup());

      consumer.showCustomOption(JavaCodeStyleSettings.class, "JD_ADD_BLANK_AFTER_DESCRIPTION",
                                JavaFrontbackBundle.message("checkbox.after.description"),
                                JavaDocFormattingPanel.getBlankLinesGroup());
      consumer.showCustomOption(JavaCodeStyleSettings.class, "JD_ADD_BLANK_AFTER_PARM_COMMENTS",
                                JavaFrontbackBundle.message("checkbox.after.parameter.descriptions"),
                                JavaDocFormattingPanel.getBlankLinesGroup());
      consumer.showCustomOption(JavaCodeStyleSettings.class, "JD_ADD_BLANK_AFTER_RETURN",
                                JavaFrontbackBundle.message("checkbox.after.return.tag"),
                                JavaDocFormattingPanel.getBlankLinesGroup());

      consumer.showCustomOption(JavaCodeStyleSettings.class, "JD_KEEP_INVALID_TAGS",
                                JavaFrontbackBundle.message("checkbox.keep.invalid.tags"),
                                JavaDocFormattingPanel.getInvalidTagsGroup());
      consumer.showCustomOption(JavaCodeStyleSettings.class, "JD_KEEP_EMPTY_PARAMETER",
                                JavaFrontbackBundle.message("checkbox.keep.empty.param.tags"),
                                JavaDocFormattingPanel.getInvalidTagsGroup());
      consumer.showCustomOption(JavaCodeStyleSettings.class, "JD_KEEP_EMPTY_RETURN",
                                JavaFrontbackBundle.message("checkbox.keep.empty.return.tags"),
                                JavaDocFormattingPanel.getInvalidTagsGroup());
      consumer.showCustomOption(JavaCodeStyleSettings.class, "JD_KEEP_EMPTY_EXCEPTION",
                                JavaFrontbackBundle.message("checkbox.keep.empty.throws.tags"),
                                JavaDocFormattingPanel.getInvalidTagsGroup());

      consumer.showCustomOption(JavaCodeStyleSettings.class, "JD_LEADING_ASTERISKS_ARE_ENABLED",
                                JavaFrontbackBundle.message("checkbox.enable.leading.asterisks"),
                                JavaDocFormattingPanel.getOtherGroup());
      consumer.showCustomOption(JavaCodeStyleSettings.class, "JD_USE_THROWS_NOT_EXCEPTION",
                                JavaFrontbackBundle.message("checkbox.use.throws.rather.than.exception"),
                                JavaDocFormattingPanel.getOtherGroup());
      consumer.showStandardOptions("WRAP_COMMENTS");
      consumer.showCustomOption(JavaCodeStyleSettings.class, "JD_P_AT_EMPTY_LINES",
                                JavaFrontbackBundle.message("checkbox.generate.p.on.empty.lines"),
                                JavaDocFormattingPanel.getOtherGroup());
      consumer.showCustomOption(JavaCodeStyleSettings.class, "JD_KEEP_EMPTY_LINES",
                                JavaFrontbackBundle.message("checkbox.keep.empty.lines"),
                                JavaDocFormattingPanel.getOtherGroup());
      consumer.showCustomOption(JavaCodeStyleSettings.class, "JD_DO_NOT_WRAP_ONE_LINE_COMMENTS",
                                JavaFrontbackBundle.message("checkbox.do.not.wrap.one.line.comments"),
                                JavaDocFormattingPanel.getOtherGroup());
      consumer.showCustomOption(JavaCodeStyleSettings.class, "JD_PRESERVE_LINE_FEEDS",
                                JavaFrontbackBundle.message("checkbox.preserve.line.feeds"),
                                JavaDocFormattingPanel.getOtherGroup());
      consumer.showCustomOption(JavaCodeStyleSettings.class, "JD_PARAM_DESCRIPTION_ON_NEW_LINE",
                                JavaFrontbackBundle.message("checkbox.param.description.on.new.line"),
                                JavaDocFormattingPanel.getOtherGroup());
      consumer.showCustomOption(JavaCodeStyleSettings.class, "JD_INDENT_ON_CONTINUATION",
                                JavaFrontbackBundle.message("checkbox.param.indent.on.continuation"),
                                JavaDocFormattingPanel.getOtherGroup());


    }
    else {
      consumer.showAllStandardOptions();
    }
  }

  @Override
  public PsiFile createFileFromText(final @NotNull Project project, final @NotNull String text) {
    final PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(
      "sample.java", JavaFileType.INSTANCE, text, LocalTimeCounter.currentTime(), false, false
    );
    file.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.HIGHEST);
    return file;
  }


  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    return new JavaIndentOptionsEditor();
  }


  @Override
  public @NotNull DocCommentSettings getDocCommentSettings(@NotNull CodeStyleSettings rootSettings) {
    return new DocCommentSettings() {
      private final JavaCodeStyleSettings mySettings =
        rootSettings.getCustomSettings(JavaCodeStyleSettings.class);

      @Override
      public boolean isDocFormattingEnabled() {
        return mySettings.ENABLE_JAVADOC_FORMATTING;
      }

      @Override
      public void setDocFormattingEnabled(boolean formattingEnabled) {
        mySettings.ENABLE_JAVADOC_FORMATTING = formattingEnabled;
      }

      @Override
      public boolean isLeadingAsteriskEnabled() {
        return mySettings.JD_LEADING_ASTERISKS_ARE_ENABLED;
      }

      @Override
      public boolean isRemoveEmptyTags() {
        return mySettings.JD_KEEP_EMPTY_EXCEPTION || mySettings.JD_KEEP_EMPTY_PARAMETER || mySettings.JD_KEEP_EMPTY_RETURN;
      }

      @Override
      public void setRemoveEmptyLines(boolean removeEmptyLines) {
        mySettings.setKeepTrailingEmptyLines(!removeEmptyLines);
      }

      @Override
      public void setRemoveEmptyTags(boolean removeEmptyTags) {
        mySettings.JD_KEEP_EMPTY_RETURN = !removeEmptyTags;
        mySettings.JD_KEEP_EMPTY_PARAMETER = !removeEmptyTags;
        mySettings.JD_KEEP_EMPTY_EXCEPTION = !removeEmptyTags;
      }
    };

  }

  @Override
  public @Nullable CodeStyleFieldAccessor getAccessor(@NotNull Object codeStyleObject, @NotNull Field field) {
    if (PackageEntryTable.class.isAssignableFrom(field.getType())) {
      return new JavaPackageEntryTableAccessor(codeStyleObject, field);
    }
    return super.getAccessor(codeStyleObject, field);
  }

  @Override
  public List<CodeStylePropertyAccessor> getAdditionalAccessors(@NotNull Object codeStyleObject) {
    if (codeStyleObject instanceof JavaCodeStyleSettings) {
      return Collections.singletonList(new RepeatAnnotationsAccessor((JavaCodeStyleSettings)codeStyleObject));
    }
    return super.getAdditionalAccessors(codeStyleObject);
  }

  private static class RepeatAnnotationsAccessor extends CodeStylePropertyAccessor<List<String>> {

    private final JavaCodeStyleSettings mySettings;

    RepeatAnnotationsAccessor(@NotNull JavaCodeStyleSettings settings) {
      mySettings = settings;
    }

    @Override
    public boolean set(@NotNull List<String> extVal) {
      mySettings.setRepeatAnnotations(extVal);
      return true;
    }

    @Override
    public @Nullable List<String> get() {
      return mySettings.getRepeatAnnotations();
    }

    @Override
    protected @Unmodifiable List<String> parseString(@NotNull String string) {
      return CodeStylePropertiesUtil.getValueList(string);
    }

    @Override
    protected @Nullable String valueToString(@NotNull List<String> value) {
      return CodeStylePropertiesUtil.toCommaSeparatedString(value);
    }

    @Override
    public String getPropertyName() {
      return "repeat_annotations";
    }
  }

  @Override
  public boolean usesCommonKeepLineBreaks() {
    return true;
  }

  private static final String GENERAL_CODE_SAMPLE =
    """
      public class Foo {
        public int[] X = new int[]{1, 3, 5, 7, 9, 11};

        public void foo(boolean a, int x, int y, int z) {
          label1:
          do {
            try {
              if (x > 0) {
                int someVariable = a ? x : y;
                int anotherVariable = a ? x : y;
              }
              else if (x < 0) {
                int someVariable = (y + z);
                someVariable = x = x + y;
              }
              else {
                label2:
                for (int i = 0; i < 5; i++) doSomething(i);
              }
              switch (a) {
                case 0:
                  doCase0();
                  break;
                default:
                  doDefault();
              }
            }
            catch (Exception e) {
              processException(e.getMessage(), x + y, z, a);
            }
            finally {
              processFinally();
            }
          }
          while (true);

          if (2 < 3) return;
          if (3 < 4) return;
          do {
            x++;
          }
          while (x < 10000);
          while (x < 50000) x++;
          for (int i = 0; i < 5; i++) System.out.println(i);
        }

        private class InnerClass implements I1, I2 {
          public void bar() throws E1, E2 {
          }
        }
      }""";

  private static final String BLANK_LINE_SAMPLE =
    """
      /*
       * This is a sample file.
       */
      package com.intellij.samples;

      import com.intellij.idea.Main;

      import javax.swing.*;
      import java.util.Vector;
      import org.jetbrains.annotations.NotNull;
      import org.jetbrains.annotations.Nullable;

      public class Foo {
        private int field1;
        private int field2;

        {
            field1 = 2;
        }

        public void foo1() {
            new Runnable() {
                public void run() {
                }
            };
        }

        public class InnerClass {
        }
      }
      class AnotherClass {
      }
      
      public class ClassWithAnnotatedFields {
          @NotNull
          public Boolean publicAnnotatedField = true;
          public Boolean publicNonAnnotatedField = true;
          @NotNull Boolean typeAnnotatedField = false;
          @NotNull
          private Boolean firstPrivateAnnotatedField = true;
          @NotNull
          private Boolean secondPrivateAnnotatedField = true;
      }
      
      public record RecordWithAnnotatedComponents(@NotNull Double s, @Nullable String t, @NotNull Double w) {}
      
      interface TestInterface {
          int MAX = 10;
          int MIN = 1;
          void method1();
          void method2();
      }""";

  private static final String SPACING_SAMPLE =
    """
      @Annotation(param1 = "value1", param2 = "value2")
      @SuppressWarnings({"ALL"})
      public class Foo<T extends Bar & Abba, U> {
        int[] X = new int[]{1, 3, 5, 6, 7, 87, 1213, 2};
        int[] empty = new int[]{};
        public void foo(int x, int y) {
          Runnable r = () -> {};
          Runnable r1 = this :: bar;
          for (int i = 0; i < x; i++) {
            y += (y ^ 0x123) << 2;
          }
          for (int a: X) { System.out.print(a); }
          do {
            try(MyResource r1 = getResource(); MyResource r2 = null) {
              if (0 < x && x < 10) {
                while (x != y) {
                  x = f(x * 3 + 5);
                }
              }
              else {
                synchronized (this) {
                  switch (e.getCode()) {
                    //...
                  }
                }
              }
            }
            catch (MyException e) {
            }
            finally {
              int[] arr = (int[])g(y);
              x = y >= 0 ? arr[y] : -1;
              Map<String, String> sMap = new HashMap<String, String>();
              Bar.<String, Integer>mess(null);
            }
          }
          while (true);
         \s
          switch (o) {
            case Rec(String s, int i) r -> {}
          }

        }
        void bar(){{return;}}
      }
      class Bar {
          static <U, T> U mess(T t) {
              return null;
          }
      }
      interface Abba {}
      public record Rec(String s, int i) {}
      
      class SimpleClass {
        class EmptyClass{}
      
        void emptyMethod() {}
      
        void complexMethodWithEmptyCodeBlocks() {
            try {} catch (Exception e) {}
            Runnable r = () -> {};
        }
      
        void oneLineMethod() {int x = 10;}
      
        void complexMethodWithOneLineCodeBlocks() {
            try {int x = 10;} catch (Exception e) {int y = 10;}
      
            Runnable r = () -> {int z = 30;};
        }
      }""";

  @SuppressWarnings({"UnusedLabel", "InnerClassMayBeStatic"})
  @org.intellij.lang.annotations.Language("JAVA") private static final String WRAPPING_CODE_SAMPLE =
    """
      /*
       * This is a sample file.
       */

      public class ThisIsASampleClass extends C1 implements I1, I2, I3, I4, I5 {
        private int f1 = 1;
        private String field2 = "";
        public void foo1(int i1, int i2, int i3, int i4, int i5, int i6, int i7) {}
        public void fooNonEmptyBody() {int x = 1;}
        public static void longerMethod() throws Exception1, Exception2, Exception3 {
      // todo something
          int
      i = 0;
          int[] a = new int[] {1, 2, 0x0052, 0x0053, 0x0054};
          int[] empty = new int[] {};
          int var1 = 1; int var2 = 2;
          foo1(0x0051, 0x0052, 0x0053, 0x0054, 0x0055, 0x0056, 0x0057);
          int x = (3 + 4 + 5 + 6) * (7 + 8 + 9 + 10) * (11 + 12 + 13 + 14 + 0xFFFFFFFF);
          String s1, s2, s3;
          s1 = s2 = s3 = "012345678901456";
          assert i + j + k + l + n+ m <= 2 : "assert description";    int y = 2 > 3 ? 7 + 8 + 9 : 11 + 12 + 13;
          super.getFoo().foo().getBar().bar();

          label:     if (2 < 3) {return;} else if (2 > 3) return; else return;
          for (int i = 0; i < 0xFFFFFF; i += 2) System.out.println(i);
          while (x < 50000) x++;
          do x++; while (x < 10000);
          switch (a) {
          case 0: case 1:
      doCase0(); break;
      case 2: case 3: return;    default:
            doDefault();
          }
          try (MyResource r1 = getResource(); MyResource r2 = null) {
            doSomething();
          } catch (Exception e) {
            processException(e);
          } finally {
            processFinally();
          }
          do {
              x--;
          } while (x > 10);\s
          try (MyResource r1 = getResource();
            MyResource r2 = null) {
            doSomething();
          }
          Runnable r = () -> {};
        }
          public static void test()\s
              throws Exception {\s
              foo.foo().bar("arg1",\s
                            "arg2");\s
              new Object() {};    }\s
          class TestInnerClass {}
          interface TestInnerInterface {}
      }

      enum Breed {
          Dalmatian(), Labrador(), Dachshund()
      }
      
      public record RecordWithAnnotatedComponents(@Annotation1 @Annotation2 String s, @Annotation1 @Annotation3(param1="value1", param2="value2") Integer t, @Annotation3(param1="value1", param2="value2") @Annotation1 Double u, @Annotation3(param1="value1", param2="value2") @Annotation5(param1="value1", param2="value2") Float w) {}

      @Annotation1 @Annotation2 @Annotation3(param1="value1", param2="value2") @Annotation4 class Foo {
          @Annotation1 @Annotation3(param1="value1", param2="value2") public static void foo(){
          }
          @Annotation1 @Annotation3(param1="value1", param2="value2") public static int myFoo;
          public void method(@Annotation1 @Annotation3(param1="value1", param2="value2") final int param){
              @Annotation1 @Annotation3(param1="value1", param2="value2") final int localVariable;    }
      }""";
}
