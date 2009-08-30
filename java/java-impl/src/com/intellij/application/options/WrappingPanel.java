package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;

/**
 * @author max
 */
public class WrappingPanel extends OptionTableWithPreviewPanel {
  private static final String METHOD_PARAMETERS_WRAPPING = ApplicationBundle.message("combobox.wrap.method.declaration.parameters");
  private static final String CALL_PARAMETERS_WRAPPING = ApplicationBundle.message("combobox.wrap.method.call.arguments");
  private static final String CALL_CHAIN_WRAPPING = ApplicationBundle.message("combobox.wrap.chained.method.calls");
  private static final String FOR_STATEMENT_WRAPPING = ApplicationBundle.message("combobox.wrap.for.statement");
  private static final String BINARY_OPERATION_WRAPPING = ApplicationBundle.message("combobox.wrap.binary.operations");
  private static final String[] FULL_WRAP_OPTIONS = new String[] {
    ApplicationBundle.message("combobox.codestyle.do.not.wrap"),
    ApplicationBundle.message("combobox.codestyle.wrap.if.long"),
    ApplicationBundle.message("combobox.codestyle.chop.down.if.long"),
    ApplicationBundle.message("combobox.codestyle.wrap.always")
  };
  private static final String[] SINGLE_ITEM_WRAP_OPTIONS = new String[]{
    ApplicationBundle.message("combobox.codestyle.do.not.wrap"),
    ApplicationBundle.message("combobox.codestyle.wrap.if.long"),
    ApplicationBundle.message("combobox.codestyle.wrap.always")
  };
  private static final int[] FULL_WRAP_VALUES = new int[]{CodeStyleSettings.DO_NOT_WRAP,
                                                          CodeStyleSettings.WRAP_AS_NEEDED,
                                                          CodeStyleSettings.WRAP_AS_NEEDED |
                                                          CodeStyleSettings.WRAP_ON_EVERY_ITEM,
                                                          CodeStyleSettings.WRAP_ALWAYS};
  private static final int[] SINGLE_ITEM_WRAP_VALUES = new int[]{CodeStyleSettings.DO_NOT_WRAP,
                                                                 CodeStyleSettings.WRAP_AS_NEEDED,
                                                                 CodeStyleSettings.WRAP_ALWAYS};
  private static final String EXTENDS_LIST_WRAPPING = ApplicationBundle.message("combobox.wrap.extends.implements.list");
  private static final String EXTENDS_KEYWORD_WRAPPING = ApplicationBundle.message("combobox.wrap.extends.implements.keyword");
  private static final String THROWS_LIST_WRAPPING = ApplicationBundle.message("combobox.wrap.throws.list");
  private static final String THROWS_KEYWORD_WRAPPING = ApplicationBundle.message("combobox.wrap.throws.keyword");
  private static final String PARENTHESIZED_EXPRESSION = ApplicationBundle.message("combobox.wrap.parenthesized.expression");
  private static final String TERNARY_OPERATION_WRAPPING = ApplicationBundle.message("combobox.wrap.ternary.operation");
  private static final String ASSIGNMENT_WRAPPING = ApplicationBundle.message("combobox.wrap.assignment.statement");
  private static final String ARRAY_INITIALIZER_WRAPPING = ApplicationBundle.message("combobox.wrap.array.initializer");
  private static final String LABELED_STATEMENT_WRAPPING = ApplicationBundle.message("combobox.wrap.label.declaration");
  private static final String MODIFIER_LIST_WRAPPING = ApplicationBundle.message("combobox.wrap.modifier.list");
  private static final String ASSERT_STATEMENT_WRAPPING = ApplicationBundle.message("combobox.wrap.assert.statement");

  public WrappingPanel(CodeStyleSettings settings) {
    super(settings);
  }

  protected void initTables() {
    initRadioGroupField("EXTENDS_LIST_WRAP", EXTENDS_LIST_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initRadioGroupField("EXTENDS_KEYWORD_WRAP", EXTENDS_KEYWORD_WRAPPING, SINGLE_ITEM_WRAP_OPTIONS,
                        SINGLE_ITEM_WRAP_VALUES);

    initRadioGroupField("METHOD_PARAMETERS_WRAP", METHOD_PARAMETERS_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.new.line.after.lpar"), METHOD_PARAMETERS_WRAPPING);
    initBooleanField("METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.place.rpar.on.new.line"), METHOD_PARAMETERS_WRAPPING);

    initRadioGroupField("THROWS_LIST_WRAP", THROWS_LIST_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initRadioGroupField("THROWS_KEYWORD_WRAP", THROWS_KEYWORD_WRAPPING, SINGLE_ITEM_WRAP_OPTIONS,
                        SINGLE_ITEM_WRAP_VALUES);

    initRadioGroupField("CALL_PARAMETERS_WRAP", CALL_PARAMETERS_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("PREFER_PARAMETERS_WRAP", ApplicationBundle.message("checkbox.wrap.take.priority.over.call.chain.wrapping"), CALL_PARAMETERS_WRAPPING);
    initBooleanField("CALL_PARAMETERS_LPAREN_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.new.line.after.lpar"), CALL_PARAMETERS_WRAPPING);
    initBooleanField("CALL_PARAMETERS_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.place.rpar.on.new.line"), CALL_PARAMETERS_WRAPPING);

    initRadioGroupField("METHOD_CALL_CHAIN_WRAP", CALL_CHAIN_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);

    initRadioGroupField("FOR_STATEMENT_WRAP", FOR_STATEMENT_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("FOR_STATEMENT_LPAREN_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.new.line.after.lpar"), FOR_STATEMENT_WRAPPING);
    initBooleanField("FOR_STATEMENT_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.place.rpar.on.new.line"), FOR_STATEMENT_WRAPPING);

    initRadioGroupField("BINARY_OPERATION_WRAP", BINARY_OPERATION_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("BINARY_OPERATION_SIGN_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.operation.sign.on.next.line"), BINARY_OPERATION_WRAPPING);

    initRadioGroupField("ASSIGNMENT_WRAP", ASSIGNMENT_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.assignment.sign.on.next.line"), ASSIGNMENT_WRAPPING);

    initRadioGroupField("TERNARY_OPERATION_WRAP", TERNARY_OPERATION_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("TERNARY_OPERATION_SIGNS_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.quest.and.colon.signs.on.next.line"),
                     TERNARY_OPERATION_WRAPPING);

    initBooleanField("PARENTHESES_EXPRESSION_LPAREN_WRAP", ApplicationBundle.message("checkbox.wrap.new.line.after.lpar"), PARENTHESIZED_EXPRESSION);
    initBooleanField("PARENTHESES_EXPRESSION_RPAREN_WRAP", ApplicationBundle.message("checkbox.wrap.place.rpar.on.new.line"), PARENTHESIZED_EXPRESSION);

    initRadioGroupField("ARRAY_INITIALIZER_WRAP", ARRAY_INITIALIZER_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.new.line.after.lbrace"), ARRAY_INITIALIZER_WRAPPING);
    initBooleanField("ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.place.rbrace.on.new.line"), ARRAY_INITIALIZER_WRAPPING);

    initRadioGroupField("LABELED_STATEMENT_WRAP", LABELED_STATEMENT_WRAPPING, SINGLE_ITEM_WRAP_OPTIONS, SINGLE_ITEM_WRAP_VALUES);

    initBooleanField("MODIFIER_LIST_WRAP", ApplicationBundle.message("checkbox.wrap.after.modifier.list"), MODIFIER_LIST_WRAPPING);

    initRadioGroupField("ASSERT_STATEMENT_WRAP", ASSERT_STATEMENT_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("ASSERT_STATEMENT_COLON_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.colon.signs.on.next.line"), ASSERT_STATEMENT_WRAPPING);

    initRadioGroupField("CLASS_ANNOTATION_WRAP", ApplicationBundle.message("checkbox.wrap.classes.annotation"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initRadioGroupField("METHOD_ANNOTATION_WRAP", ApplicationBundle.message("checkbox.wrap.methods.annotation"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initRadioGroupField("FIELD_ANNOTATION_WRAP", ApplicationBundle.message("checkbox.wrap.fields.annotation"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initRadioGroupField("PARAMETER_ANNOTATION_WRAP", ApplicationBundle.message("checkbox.wrap.parameters.annotation"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initRadioGroupField("VARIABLE_ANNOTATION_WRAP", ApplicationBundle.message("checkbox.wrap.local.variables.annotation"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initRadioGroupField("ENUM_CONSTANTS_WRAP", ApplicationBundle.message("checkbox.wrap.enum.constants"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    
  }

  protected int getRightMargin() {
    return 37;
  }

  protected String getPreviewText() {          //| Margin is here
    return "/*\n" +
           " * This is a sample file.\n" +
           " */\n" +
           "\n" +
           "public class ThisIsASampleClass extends C1 implements I1, I2, I3, I4, I5 {\n" +
           "  private int f1;\n" +
           "  private int f2;\n" +
           "  public void foo1(int i1, int i2, int i3, int i4, int i5, int i6, int i7) {}\n" +
           "  public static void longerMethod() throws Exception1, Exception2, Exception3 {\n" +
           "    int[] a = new int[] {1, 2, 0x0052, 0x0053, 0x0054};\n" +
           "    foo1(0x0051, 0x0052, 0x0053, 0x0054, 0x0055, 0x0056, 0x0057);\n" +
           "    int x = (3 + 4 + 5 + 6) * (7 + 8 + 9 + 10) * (11 + 12 + 13 + 14 + 0xFFFFFFFF);\n" +
           "    String s1, s2, s3;\n" +
           "    s1 = s2 = s3 = \"012345678901456\";\n" +
           "    assert i + j + k + l + n+ m <= 2 : \"assert description\";" +
           "    int y = 2 > 3 ? 7 + 8 + 9 : 11 + 12 + 13;\n" +
           "    label: " +
           "    for (int i = 0; i < 0xFFFFFF; i += 2) {\n" +
           "       super.getFoo().foo().getBar().bar();\n" +
           "    }\n" +
           "  }\n" +
           "}\n" +
           "\n" +
           "enum Breed {\n" +
           "    Dalmatian(), Labrador(), Dachshund()\n" +
           "}\n" +
           "\n" +
           "@Annotation1 @Annotation2 @Annotation3(param1=\"value1\", param2=\"value2\") @Annotation4 class Foo {\n" +
           "    @Annotation1 @Annotation3(param1=\"value1\", param2=\"value2\") public static void foo(){\n" +
           "    }\n" +
           "    @Annotation1 @Annotation3(param1=\"value1\", param2=\"value2\") public static int myFoo;\n" +
           "    public void method(@Annotation1 @Annotation3(param1=\"value1\", param2=\"value2\") final int param){\n" +
           "        @Annotation1 @Annotation3(param1=\"value1\", param2=\"value2\") final int localVariable;" +
           "    }\n" +      
           "}";
  }
}
