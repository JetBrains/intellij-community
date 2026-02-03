// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.colors.pages;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.java.frontend.codeInsight.highlighting.JavaColorSettingsPageBase;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.options.colors.RainbowColorSettingsPage;
import org.jetbrains.annotations.NotNull;

public final class JavaColorSettingsPage extends JavaColorSettingsPageBase implements RainbowColorSettingsPage{
  @Override
  public @NotNull String getDemoText() {
    return
      """
        /* Block comment */
        import <class>java.util.Date</class>;
        import static <interface>AnInterface</interface>.<static_final>CONSTANT</static_final>;
        import static <class>java.util.Date</class>.<static_method>parse</static_method>;
        import static <class>SomeClass</class>.<static>staticField</static>;
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
          protected final int protectedField = 0;
          final int packagePrivateField = 0;
        
          /**""" +
      RainbowHighlighter.generatePaletteExample("\n   * ") +
      """
        
           * @param <javadocTagValue>param1</javadocTagValue>
           * @param <javadocTagValue>param2</javadocTagValue>
           * @param <javadocTagValue>param3</javadocTagValue>
           */
          public <constructorDeclaration>SomeClass</constructorDeclaration>(<interface>AnInterface</interface> <param>param1</param>,
                          int <param>param2</param>,
                          int <param>param3</param>) {
            int <reassignedLocalVar>reassignedValue</reassignedLocalVar> = this.<warning>staticField</warning> + <param>param2</param> + <param>param3</param>;
            long <localVar>localVar1</localVar>, <localVar>localVar2</localVar>, <localVar>localVar3</localVar>, <localVar>localVar4</localVar>;
            <error>int <localVar>localVar</localVar> = "IntelliJ"</error>; // Error, incompatible types
            <class>System</class>.<static>out</static>.<methodCall>println</methodCall>(<private><field>anotherString</field></private> + <inherited_method>toString</inherited_method>() + <localVar>localVar</localVar>);
            int <localVar>sum</localVar> = <protected><field>protectedField</field></protected> + <package_private><field>packagePrivateField</field></package_private> + <public><static>staticField</static></public>;
            long <localVar>time</localVar> = <static_imported_method><deprecated>parse</deprecated></static_imported_method>("1.2.3"); // Method is deprecated
            new <class>Thread</class>().<for_removal>countStackFrames</for_removal>(); // Method is deprecated and marked for removal
            <reassignedLocalVar>reassignedValue</reassignedLocalVar> ++;\s
            <field>field</field>.<abstract_method>run</abstract_method>();\s
            new <anonymousClass>SomeClass</anonymousClass>() {
              {
                int <localVar>a</localVar> = <implicitAnonymousParameter>localVar</implicitAnonymousParameter>;
              }
            };
            int[] <localVar>l</localVar> = new <constructorCall>ArrayList</constructorCall><<class>String</class>>().<methodCall>toArray</methodCall>(new int[<staticallyConstImported>CONSTANT</staticallyConstImported>]);
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
          protected int <field>instanceField</field> = <staticallyImported>staticField</staticallyImported>;
        }""";
  }

  @Override
  public boolean isRainbowType(TextAttributesKey type) {
    return JavaHighlightingColors.LOCAL_VARIABLE_ATTRIBUTES.equals(type)
        || JavaHighlightingColors.REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES.equals(type)
        || JavaHighlightingColors.PARAMETER_ATTRIBUTES.equals(type)
        || JavaHighlightingColors.REASSIGNED_PARAMETER_ATTRIBUTES.equals(type)
        || JavaHighlightingColors.DOC_COMMENT_TAG_VALUE.equals(type);
  }

  @Override
  public @NotNull Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
