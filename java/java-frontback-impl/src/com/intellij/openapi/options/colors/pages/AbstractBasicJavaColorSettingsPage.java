// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.colors.pages;

import com.intellij.application.options.colors.InspectionColorSettingsPage;
import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.core.JavaOptionBundle;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

public abstract class AbstractBasicJavaColorSettingsPage
  implements ColorSettingsPage, InspectionColorSettingsPage, DisplayPrioritySortable {
  private static final AttributesDescriptor[] ourDescriptors = {
    new AttributesDescriptor(OptionsBundle.messagePointer("options.java.attribute.descriptor.keyword"), JavaHighlightingColors.KEYWORD),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.java.attribute.descriptor.number"), JavaHighlightingColors.NUMBER),

    new AttributesDescriptor(OptionsBundle.messagePointer("options.java.attribute.descriptor.string"), JavaHighlightingColors.STRING),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.java.attribute.descriptor.valid.escape.in.string"), JavaHighlightingColors.VALID_STRING_ESCAPE),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.java.attribute.descriptor.invalid.escape.in.string"), JavaHighlightingColors.INVALID_STRING_ESCAPE),

    new AttributesDescriptor(OptionsBundle.messagePointer("options.java.attribute.descriptor.operator.sign"), JavaHighlightingColors.OPERATION_SIGN),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.java.attribute.descriptor.parentheses"), JavaHighlightingColors.PARENTHESES),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.java.attribute.descriptor.braces"), JavaHighlightingColors.BRACES),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.java.attribute.descriptor.brackets"), JavaHighlightingColors.BRACKETS),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.java.attribute.descriptor.comma"), JavaHighlightingColors.COMMA),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.java.attribute.descriptor.semicolon"), JavaHighlightingColors.JAVA_SEMICOLON),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.java.attribute.descriptor.dot"), JavaHighlightingColors.DOT),

    new AttributesDescriptor(OptionsBundle.messagePointer("options.java.attribute.descriptor.line.comment"), JavaHighlightingColors.LINE_COMMENT),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.java.attribute.descriptor.block.comment"), JavaHighlightingColors.JAVA_BLOCK_COMMENT),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.javadoc.comment"), JavaHighlightingColors.DOC_COMMENT),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.javadoc.tag"), JavaHighlightingColors.DOC_COMMENT_TAG),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.javadoc.tag.value"), JavaHighlightingColors.DOC_COMMENT_TAG_VALUE),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.javadoc.markup"), JavaHighlightingColors.DOC_COMMENT_MARKUP),

    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.class"), JavaHighlightingColors.CLASS_NAME_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.anonymous.class"), JavaHighlightingColors.ANONYMOUS_CLASS_NAME_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.type.parameter"), JavaHighlightingColors.TYPE_PARAMETER_NAME_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.abstract.class"), JavaHighlightingColors.ABSTRACT_CLASS_NAME_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.interface"), JavaHighlightingColors.INTERFACE_NAME_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.enum"), JavaHighlightingColors.ENUM_NAME_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.record"), JavaHighlightingColors.RECORD_NAME_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.java.attribute.descriptor.local.variable"), JavaHighlightingColors.LOCAL_VARIABLE_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.reassigned.local.variable"), JavaHighlightingColors.REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.reassigned.parameter"), JavaHighlightingColors.REASSIGNED_PARAMETER_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.implicit.anonymous.parameter"), JavaHighlightingColors.IMPLICIT_ANONYMOUS_CLASS_PARAMETER_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.instance.field"), JavaHighlightingColors.INSTANCE_FIELD_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.instance.final.field"), JavaHighlightingColors.INSTANCE_FINAL_FIELD_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.record.component"), JavaHighlightingColors.RECORD_COMPONENT_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.static.field"), JavaHighlightingColors.STATIC_FIELD_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.static.imported.field"), JavaHighlightingColors.STATIC_FIELD_IMPORTED_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.static.final.field"), JavaHighlightingColors.STATIC_FINAL_FIELD_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.static.final.imported.field"), JavaHighlightingColors.STATIC_FINAL_FIELD_IMPORTED_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.java.attribute.descriptor.parameter"), JavaHighlightingColors.PARAMETER_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.lambda.parameter"), JavaHighlightingColors.LAMBDA_PARAMETER_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.method.call"), JavaHighlightingColors.METHOD_CALL_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.method.imported.call"), JavaHighlightingColors.STATIC_METHOD_CALL_IMPORTED_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.method.declaration"), JavaHighlightingColors.METHOD_DECLARATION_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.constructor.call"), JavaHighlightingColors.CONSTRUCTOR_CALL_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.constructor.declaration"), JavaHighlightingColors.CONSTRUCTOR_DECLARATION_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.static.method"), JavaHighlightingColors.STATIC_METHOD_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.abstract.method"), JavaHighlightingColors.ABSTRACT_METHOD_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.inherited.method"), JavaHighlightingColors.INHERITED_METHOD_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.public"), JavaHighlightingColors.PUBLIC_REFERENCE_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.protected"), JavaHighlightingColors.PROTECTED_REFERENCE_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.package.private"), JavaHighlightingColors.PACKAGE_PRIVATE_REFERENCE_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.private"), JavaHighlightingColors.PRIVATE_REFERENCE_ATTRIBUTES),

    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.annotation.name"), JavaHighlightingColors.ANNOTATION_NAME_ATTRIBUTES),
    new AttributesDescriptor(JavaOptionBundle.messagePointer("options.java.attribute.descriptor.annotation.attribute.name"), JavaHighlightingColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES)
  };

  private static final @NonNls Map<String, TextAttributesKey> ourTags = RainbowHighlighter.createRainbowHLM();
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
    ourTags.put("record", JavaHighlightingColors.RECORD_NAME_ATTRIBUTES);
    ourTags.put("annotationName", JavaHighlightingColors.ANNOTATION_NAME_ATTRIBUTES);
    ourTags.put("annotationAttributeName", JavaHighlightingColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES);
    ourTags.put("javadocTagValue", JavaHighlightingColors.DOC_COMMENT_TAG_VALUE);
    ourTags.put("instanceFinalField", JavaHighlightingColors.INSTANCE_FINAL_FIELD_ATTRIBUTES);
    ourTags.put("recordComponent", JavaHighlightingColors.RECORD_COMPONENT_ATTRIBUTES);
    ourTags.put("staticallyConstImported", JavaHighlightingColors.STATIC_FINAL_FIELD_IMPORTED_ATTRIBUTES);
    ourTags.put("staticallyImported", JavaHighlightingColors.STATIC_FIELD_IMPORTED_ATTRIBUTES);
    ourTags.put("static_imported_method", JavaHighlightingColors.STATIC_METHOD_CALL_IMPORTED_ATTRIBUTES);
    ourTags.put("public", JavaHighlightingColors.PUBLIC_REFERENCE_ATTRIBUTES);
    ourTags.put("protected", JavaHighlightingColors.PROTECTED_REFERENCE_ATTRIBUTES);
    ourTags.put("package_private", JavaHighlightingColors.PACKAGE_PRIVATE_REFERENCE_ATTRIBUTES);
    ourTags.put("private", JavaHighlightingColors.PRIVATE_REFERENCE_ATTRIBUTES);
  }

  @Override
  public @NotNull String getDisplayName() {
    return JavaOptionBundle.message("options.java.display.name");
  }

  @Override
  public abstract Icon getIcon();

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return ourDescriptors;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }


  /**
   * Note: this method is overridden in {@link JavaColorSettingsPage}.
   * Any changes you make here, you will probably want to do there as well.
   */
  @Override
  public @NotNull String getDemoText() {
    return
      """
        /* Block comment */
        import <class>java.util.Date</class>;
        /**
         * Doc comment here for <code>SomeClass</code>
         * @param <javadocTagValue>T</javadocTagValue> type parameter
         * @see <class>Math</class>#<methodCall>sin</methodCall>(double)
         */
        <annotationName>@Annotation</annotationName> (<annotationAttributeName>name</annotationAttributeName>=value)
        public class <class>SomeClass</class><<typeParameter>T</typeParameter> extends <interface>Runnable</interface>> { // some comment
          private <typeParameter>T</typeParameter> <field>field</field> = null;
          private double <unusedField>unusedField</unusedField> = 12345.67890;
          private <unknownType>UnknownType</unknownType> <field>anotherString</field> = "Another\\nStrin\\g";
          public static int <static>staticField</static> = 0;
          public final int <instanceFinalField>instanceFinalField</instanceFinalField> = 0;

          public <constructorDeclaration>SomeClass</constructorDeclaration>(<interface>AnInterface</interface> <param>param</param>, int[] <reassignedParameter>reassignedParam</reassignedParameter>) {
            <error>int <localVar>localVar</localVar> = "IntelliJ"</error>; // Error, incompatible types
            <class>System</class>.<static>out</static>.<methodCall>println</methodCall>(<field>anotherString</field> + <inherited_method>toString</inherited_method>() + <localVar>localVar</localVar>);
            long <localVar>time</localVar> = <class>Date</class>.<static_method><deprecated>parse</deprecated></static_method>("1.2.3"); // Method is deprecated
            int <reassignedLocalVar>reassignedValue</reassignedLocalVar> = this.<warning>staticField</warning>;\s
            <reassignedLocalVar>reassignedValue</reassignedLocalVar> ++;\s
            <field>field</field>.<abstract_method>run</abstract_method>();\s
            new <anonymousClass>SomeClass</anonymousClass>() {
              {
                int <localVar>a</localVar> = <implicitAnonymousParameter>localVar</implicitAnonymousParameter>;
              }
            };
            <reassignedParameter>reassignedParam</reassignedParameter> = new <constructorCall>ArrayList</constructorCall><<class>String</class>>().toArray(new int[0]);
          }
        }
        enum <enum>AnEnum</enum> { <static_final>CONST1</static_final>, <static_final>CONST2</static_final> }
        interface <interface>AnInterface</interface> {
          int <static_final>CONSTANT</static_final> = 2;
          void <methodDeclaration>method</methodDeclaration>();
        }
        @interface <annotationName>AnnotationType</annotationName> {}
        record <record>Point</record>(int <recordComponent>x</recordComponent>, int <recordComponent>y</recordComponent>) {}
        abstract class <abstractClass>SomeAbstractClass</abstractClass> {
        }""";
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
