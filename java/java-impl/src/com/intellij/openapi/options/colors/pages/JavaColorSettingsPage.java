/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.options.colors.pages;

import com.intellij.application.options.colors.InspectionColorSettingsPage;
import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class JavaColorSettingsPage implements ColorSettingsPage, InspectionColorSettingsPage, DisplayPrioritySortable {
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
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.javadoc.comment"), JavaHighlightingColors.DOC_COMMENT),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.javadoc.tag"), JavaHighlightingColors.DOC_COMMENT_TAG),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.javadoc.tag.value"), JavaHighlightingColors.DOC_COMMENT_TAG_VALUE),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.javadoc.markup"), JavaHighlightingColors.DOC_COMMENT_MARKUP),

    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.class"), JavaHighlightingColors.CLASS_NAME_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.anonymous.class"), JavaHighlightingColors.ANONYMOUS_CLASS_NAME_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.type.parameter"), JavaHighlightingColors.TYPE_PARAMETER_NAME_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.abstract.class"), JavaHighlightingColors.ABSTRACT_CLASS_NAME_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.interface"), JavaHighlightingColors.INTERFACE_NAME_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.enum"), JavaHighlightingColors.ENUM_NAME_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.local.variable"), JavaHighlightingColors.LOCAL_VARIABLE_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.reassigned.local.variable"), JavaHighlightingColors.REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.reassigned.parameter"), JavaHighlightingColors.REASSIGNED_PARAMETER_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.implicit.anonymous.parameter"), JavaHighlightingColors.IMPLICIT_ANONYMOUS_CLASS_PARAMETER_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.instance.field"), JavaHighlightingColors.INSTANCE_FIELD_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.instance.final.field"), JavaHighlightingColors.INSTANCE_FINAL_FIELD_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.static.field"), JavaHighlightingColors.STATIC_FIELD_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.static.final.field"), JavaHighlightingColors.STATIC_FINAL_FIELD_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.parameter"), JavaHighlightingColors.PARAMETER_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.lambda.parameter"), JavaHighlightingColors.LAMBDA_PARAMETER_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.method.call"), JavaHighlightingColors.METHOD_CALL_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.method.declaration"), JavaHighlightingColors.METHOD_DECLARATION_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.constructor.call"), JavaHighlightingColors.CONSTRUCTOR_CALL_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.constructor.declaration"), JavaHighlightingColors.CONSTRUCTOR_DECLARATION_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.static.method"), JavaHighlightingColors.STATIC_METHOD_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.abstract.method"), JavaHighlightingColors.ABSTRACT_METHOD_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.inherited.method"), JavaHighlightingColors.INHERITED_METHOD_ATTRIBUTES),

    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.annotation.name"), JavaHighlightingColors.ANNOTATION_NAME_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.annotation.attribute.name"), JavaHighlightingColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES)
  };

  @NonNls private static final Map<String, TextAttributesKey> ourTags = new HashMap<>();
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
    ourTags.put("reassignedParameter", JavaHighlightingColors.REASSIGNED_PARAMETER_ATTRIBUTES);
    ourTags.put("implicitAnonymousParameter", JavaHighlightingColors.IMPLICIT_ANONYMOUS_CLASS_PARAMETER_ATTRIBUTES);
    ourTags.put("static", JavaHighlightingColors.STATIC_FIELD_ATTRIBUTES);
    ourTags.put("static_final", JavaHighlightingColors.STATIC_FINAL_FIELD_ATTRIBUTES);
    ourTags.put("deprecated", CodeInsightColors.DEPRECATED_ATTRIBUTES);
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
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return OptionsBundle.message("options.java.display.name");
  }

  @Override
  public Icon getIcon() {
    return StdFileTypes.JAVA.getIcon();
  }

  @Override
  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ourDescriptors;
  }

  @Override
  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
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
      "\n" +
      "  public <constructorDeclaration>SomeClass</constructorDeclaration>(<interface>AnInterface</interface> <param>param</param>, int[] <reassignedParameter>reassignedParam</reassignedParameter>) {\n" +
      "    <error>int <localVar>localVar</localVar> = \"IntelliJ\"</error>; // Error, incompatible types\n" +
      "    <class>System</class>.<static>out</static>.<methodCall>println</methodCall>(<field>anotherString</field> + <inherited_method>toString</inherited_method>() + <localVar>localVar</localVar>);\n" +
      "    long <localVar>time</localVar> = <class>Date</class>.<static_method><deprecated>parse</deprecated></static_method>(\"1.2.3\"); // Method is deprecated\n" +
      "    int <reassignedLocalVar>reassignedValue</reassignedLocalVar> = this.<warning>staticField</warning>; \n" +
      "    <reassignedLocalVar>reassignedValue</reassignedLocalVar> ++; \n" +
      "    <field>field</field>.<abstract_method>run</abstract_method>(); \n" +
      "    new <anonymousClass>SomeClass</anonymousClass>() {\n" +
      "      {\n" +
      "        int <localVar>a</localVar> = <implicitAnonymousParameter>localVar</implicitAnonymousParameter>;\n" +
      "      }\n" +
      "    };\n" +
      "    <reassignedParameter>reassignedParam</reassignedParameter> = new <constructorCall>ArrayList</constructorCall><<class>String</class>>().toArray(new int[0]);\n" +
      "  }\n" +
      "}\n" +
      "enum <enum>AnEnum</enum> { <static_final>CONST1</static_final>, <static_final>CONST2</static_final> }\n"+
      "interface <interface>AnInterface</interface> {\n" +
      "  int <static_final>CONSTANT</static_final> = 2;\n" +
      "  void <methodDeclaration>method</methodDeclaration>();\n" +
      "}\n" +
      "abstract class <abstractClass>SomeAbstractClass</abstractClass> {\n" +
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
}
