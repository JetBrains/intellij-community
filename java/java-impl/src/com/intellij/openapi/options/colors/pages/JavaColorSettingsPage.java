// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.colors.pages;

import com.intellij.application.options.colors.InspectionColorSettingsPage;
import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.java.JavaBundle;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.RainbowColorSettingsPage;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class JavaColorSettingsPage implements RainbowColorSettingsPage, InspectionColorSettingsPage, DisplayPrioritySortable {
  private static final AttributesDescriptor[] ourDescriptors = {
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.keyword"), JavaHighlightingColors.KEYWORD),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.number"), JavaHighlightingColors.NUMBER),

    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.string"), JavaHighlightingColors.STRING),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.valid.escape.in.string"), JavaHighlightingColors.VALID_STRING_ESCAPE),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.invalid.escape.in.string"), JavaHighlightingColors.INVALID_STRING_ESCAPE),

    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.operator.sign"), JavaHighlightingColors.OPERATION_SIGN),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.parentheses"), JavaHighlightingColors.PARENTHESES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.braces"), JavaHighlightingColors.BRACES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.brackets"), JavaHighlightingColors.BRACKETS),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.comma"), JavaHighlightingColors.COMMA),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.semicolon"), JavaHighlightingColors.JAVA_SEMICOLON),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.dot"), JavaHighlightingColors.DOT),

    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.line.comment"), JavaHighlightingColors.LINE_COMMENT),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.block.comment"), JavaHighlightingColors.JAVA_BLOCK_COMMENT),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.javadoc.comment"), JavaHighlightingColors.DOC_COMMENT),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.javadoc.tag"), JavaHighlightingColors.DOC_COMMENT_TAG),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.javadoc.tag.value"), JavaHighlightingColors.DOC_COMMENT_TAG_VALUE),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.javadoc.markup"), JavaHighlightingColors.DOC_COMMENT_MARKUP),

    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.class"), JavaHighlightingColors.CLASS_NAME_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.anonymous.class"), JavaHighlightingColors.ANONYMOUS_CLASS_NAME_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.type.parameter"), JavaHighlightingColors.TYPE_PARAMETER_NAME_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.abstract.class"), JavaHighlightingColors.ABSTRACT_CLASS_NAME_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.interface"), JavaHighlightingColors.INTERFACE_NAME_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.enum"), JavaHighlightingColors.ENUM_NAME_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.local.variable"), JavaHighlightingColors.LOCAL_VARIABLE_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.reassigned.local.variable"), JavaHighlightingColors.REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.reassigned.parameter"), JavaHighlightingColors.REASSIGNED_PARAMETER_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.implicit.anonymous.parameter"), JavaHighlightingColors.IMPLICIT_ANONYMOUS_CLASS_PARAMETER_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.instance.field"), JavaHighlightingColors.INSTANCE_FIELD_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.instance.final.field"), JavaHighlightingColors.INSTANCE_FINAL_FIELD_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.static.field"), JavaHighlightingColors.STATIC_FIELD_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.static.imported.field"), JavaHighlightingColors.STATIC_FIELD_IMPORTED_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.static.final.field"), JavaHighlightingColors.STATIC_FINAL_FIELD_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.static.final.imported.field"), JavaHighlightingColors.STATIC_FINAL_FIELD_IMPORTED_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.parameter"), JavaHighlightingColors.PARAMETER_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.lambda.parameter"), JavaHighlightingColors.LAMBDA_PARAMETER_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.method.call"), JavaHighlightingColors.METHOD_CALL_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.method.imported.call"), JavaHighlightingColors.STATIC_METHOD_CALL_IMPORTED_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.method.declaration"), JavaHighlightingColors.METHOD_DECLARATION_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.constructor.call"), JavaHighlightingColors.CONSTRUCTOR_CALL_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.constructor.declaration"), JavaHighlightingColors.CONSTRUCTOR_DECLARATION_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.static.method"), JavaHighlightingColors.STATIC_METHOD_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.abstract.method"), JavaHighlightingColors.ABSTRACT_METHOD_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.inherited.method"), JavaHighlightingColors.INHERITED_METHOD_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.public"), JavaHighlightingColors.PUBLIC_REFERENCE_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.protected"), JavaHighlightingColors.PROTECTED_REFERENCE_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.package.private"), JavaHighlightingColors.PACKAGE_PRIVATE_REFERENCE_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.private"), JavaHighlightingColors.PRIVATE_REFERENCE_ATTRIBUTES),

    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.annotation.name"), JavaHighlightingColors.ANNOTATION_NAME_ATTRIBUTES),
    new AttributesDescriptor(JavaBundle.message("options.java.attribute.descriptor.annotation.attribute.name"), JavaHighlightingColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES)
  };

  @NonNls private static final Map<String, TextAttributesKey> ourTags = RainbowHighlighter.createRainbowHLM();
  static {
    ourTags.put("field", JavaHighlightingColors.INSTANCE_FIELD_ATTRIBUTES);
    ourTags.put("unusedField", CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
    ourTags.put("error", CodeInsightColors.ERRORS_ATTRIBUTES);
    ourTags.put("warning", CodeInsightColors.WARNINGS_ATTRIBUTES);
    ourTags.put("weak_warning", CodeInsightColors.WEAK_WARNING_ATTRIBUTES);
    ourTags.put("server_problems", CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING);
    ourTags.put("server_duplicate", CodeInsightColors.DUPLICATE_FROM_SERVER);
    ourTags.put("unknownType", CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);
    ourTags.put("localVar", JavaHighlightingColors.LOCAL_VARIABLE_ATTRIBUTES);
    ourTags.put("reassignedLocalVar", JavaHighlightingColors.REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES);
    ourTags.put("implicitAnonymousParameter", JavaHighlightingColors.IMPLICIT_ANONYMOUS_CLASS_PARAMETER_ATTRIBUTES);
    ourTags.put("static", JavaHighlightingColors.STATIC_FIELD_ATTRIBUTES);
    ourTags.put("static_final", JavaHighlightingColors.STATIC_FINAL_FIELD_ATTRIBUTES);
    ourTags.put("deprecated", CodeInsightColors.DEPRECATED_ATTRIBUTES);
    ourTags.put("for_removal", CodeInsightColors.MARKED_FOR_REMOVAL_ATTRIBUTES);
    ourTags.put("constructorCall", JavaHighlightingColors.CONSTRUCTOR_CALL_ATTRIBUTES);
    ourTags.put("constructorDeclaration", JavaHighlightingColors.CONSTRUCTOR_DECLARATION_ATTRIBUTES);
    ourTags.put("methodCall", JavaHighlightingColors.METHOD_CALL_ATTRIBUTES);
    ourTags.put("methodDeclaration", JavaHighlightingColors.METHOD_DECLARATION_ATTRIBUTES);
    ourTags.put("static_method", JavaHighlightingColors.STATIC_METHOD_ATTRIBUTES);
    ourTags.put("abstract_method", JavaHighlightingColors.ABSTRACT_METHOD_ATTRIBUTES);
    ourTags.put("inherited_method", JavaHighlightingColors.INHERITED_METHOD_ATTRIBUTES);
    ourTags.put("param", JavaHighlightingColors.PARAMETER_ATTRIBUTES);
    ourTags.put("lambda_param", JavaHighlightingColors.LAMBDA_PARAMETER_ATTRIBUTES);
    ourTags.put("class", JavaHighlightingColors.CLASS_NAME_ATTRIBUTES);
    ourTags.put("anonymousClass", JavaHighlightingColors.ANONYMOUS_CLASS_NAME_ATTRIBUTES);
    ourTags.put("typeParameter", JavaHighlightingColors.TYPE_PARAMETER_NAME_ATTRIBUTES);
    ourTags.put("abstractClass", JavaHighlightingColors.ABSTRACT_CLASS_NAME_ATTRIBUTES);
    ourTags.put("interface", JavaHighlightingColors.INTERFACE_NAME_ATTRIBUTES);
    ourTags.put("enum", JavaHighlightingColors.ENUM_NAME_ATTRIBUTES);
    ourTags.put("annotationName", JavaHighlightingColors.ANNOTATION_NAME_ATTRIBUTES);
    ourTags.put("annotationAttributeName", JavaHighlightingColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES);
    ourTags.put("javadocTagValue", JavaHighlightingColors.DOC_COMMENT_TAG_VALUE);
    ourTags.put("instanceFinalField", JavaHighlightingColors.INSTANCE_FINAL_FIELD_ATTRIBUTES);
    ourTags.put("staticallyConstImported", JavaHighlightingColors.STATIC_FINAL_FIELD_IMPORTED_ATTRIBUTES);
    ourTags.put("staticallyImported", JavaHighlightingColors.STATIC_FIELD_IMPORTED_ATTRIBUTES);
    ourTags.put("static_imported_method", JavaHighlightingColors.STATIC_METHOD_CALL_IMPORTED_ATTRIBUTES);
    ourTags.put("public", JavaHighlightingColors.PUBLIC_REFERENCE_ATTRIBUTES);
    ourTags.put("protected", JavaHighlightingColors.PROTECTED_REFERENCE_ATTRIBUTES);
    ourTags.put("package_private", JavaHighlightingColors.PACKAGE_PRIVATE_REFERENCE_ATTRIBUTES);
    ourTags.put("private", JavaHighlightingColors.PRIVATE_REFERENCE_ATTRIBUTES);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return JavaBundle.message("options.java.display.name");
  }

  @Override
  public Icon getIcon() {
    return JavaFileType.INSTANCE.getIcon();
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return ourDescriptors;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new JavaFileHighlighter(LanguageLevel.HIGHEST);
  }

  @Override
  @NotNull
  public String getDemoText() {
    return
      "/* Block comment */\n" +
      "import <class>java.util.Date</class>;\n" +
      "import static <interface>AnInterface</interface>.<static_final>CONSTANT</static_final>;\n" +
      "import static <class>java.util.Date</class>.<static_method>parse</static_method>;\n" +
      "import static <class>SomeClass</class>.<static>staticField</static>;\n" +
      "/**\n" +
      " * Doc comment here for <code>SomeClass</code>\n" +
      " * @param <javadocTagValue>T</javadocTagValue> type parameter\n" +
      " * @see <class>Math</class>#<methodCall>sin</methodCall>(double)\n" +
      " */\n" +
      "<annotationName>@Annotation</annotationName> (<annotationAttributeName>name</annotationAttributeName>=value)\n" +
      "public class <class>SomeClass</class><<typeParameter>T</typeParameter> extends <interface>Runnable</interface>> { // some comment\n" +
      "  private <typeParameter>T</typeParameter> <field>field</field> = null;\n" +
      "  private double <unusedField>unusedField</unusedField> = 12345.67890;\n" +
      "  private <unknownType>UnknownType</unknownType> <field>anotherString</field> = \"Another\\nStrin\\g\";\n" +
      "  public static int <static>staticField</static> = 0;\n" +
      "  public final int <instanceFinalField>instanceFinalField</instanceFinalField> = 0;\n" +
      "  protected final int protectedField = 0;\n" +
      "  final int packagePrivateField = 0;\n" +
      "\n" +
      "  /**" +
      RainbowHighlighter.generatePaletteExample("\n   * ") + "\n" +
      "   * @param <javadocTagValue>param1</javadocTagValue>\n" +
      "   * @param <javadocTagValue>param2</javadocTagValue>\n" +
      "   * @param <javadocTagValue>param3</javadocTagValue>\n" +
      "   */\n" +
      "  public <constructorDeclaration>SomeClass</constructorDeclaration>(<interface>AnInterface</interface> <param>param1</param>,\n" +
      "                  int <param>param2</param>,\n" +
      "                  int <param>param3</param>) {\n" +
      "    int <reassignedLocalVar>reassignedValue</reassignedLocalVar> = this.<warning>staticField</warning> + <param>param2</param> + <param>param3</param>;\n" +
      "    long <localVar>localVar1</localVar>, <localVar>localVar2</localVar>, <localVar>localVar3</localVar>, <localVar>localVar4</localVar>;\n" +
      "    <error>int <localVar>localVar</localVar> = \"IntelliJ\"</error>; // Error, incompatible types\n" +
      "    <class>System</class>.<static>out</static>.<methodCall>println</methodCall>(<private><field>anotherString</field></private> + <inherited_method>toString</inherited_method>() + <localVar>localVar</localVar>);\n" +
      "    int <localVar>sum</localVar> = <protected><field>protectedField</field></protected> + <package_private><field>packagePrivateField</field></package_private> + <public><static>staticField</static></public>;\n" + 
      "    long <localVar>time</localVar> = <static_imported_method><deprecated>parse</deprecated></static_imported_method>(\"1.2.3\"); // Method is deprecated\n" +
      "    new <class>Thread</class>().<for_removal>countStackFrames</for_removal>(); // Method is deprecated and marked for removal\n" +
      "    <reassignedLocalVar>reassignedValue</reassignedLocalVar> ++; \n" +
      "    <field>field</field>.<abstract_method>run</abstract_method>(); \n" +
      "    new <anonymousClass>SomeClass</anonymousClass>() {\n" +
      "      {\n" +
      "        int <localVar>a</localVar> = <implicitAnonymousParameter>localVar</implicitAnonymousParameter>;\n" +
      "      }\n" +
      "    };\n" +
      "    int[] <localVar>l</localVar> = new <constructorCall>ArrayList</constructorCall><<class>String</class>>().<methodCall>toArray</methodCall>(new int[<staticallyConstImported>CONSTANT</staticallyConstImported>]);\n" +
      "  }\n" +
      "}\n" +
      "enum <enum>AnEnum</enum> { <static_final>CONST1</static_final>, <static_final>CONST2</static_final> }\n" +
      "interface <interface>AnInterface</interface> {\n" +
      "  int <static_final>CONSTANT</static_final> = 2;\n" +
      "  void <methodDeclaration>method</methodDeclaration>();\n" +
      "}\n" +
      "abstract class <abstractClass>SomeAbstractClass</abstractClass> {\n" +
      "  protected int <field>instanceField</field> = <staticallyImported>staticField</staticallyImported>;\n" +
      "}";
  }

  @Override
  public Map<String,TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ourTags;
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.KEY_LANGUAGE_SETTINGS;
  }

  @Override
  public boolean isRainbowType(TextAttributesKey type) {
    return JavaHighlightingColors.LOCAL_VARIABLE_ATTRIBUTES.equals(type)
        || JavaHighlightingColors.REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES.equals(type)
        || JavaHighlightingColors.PARAMETER_ATTRIBUTES.equals(type)
        || JavaHighlightingColors.REASSIGNED_PARAMETER_ATTRIBUTES.equals(type)
        || JavaHighlightingColors.DOC_COMMENT_TAG_VALUE.equals(type);
  }

  @Nullable
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
