// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.colors.pages;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.RainbowColorSettingsPage;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class JavaColorSettingsPage extends AbstractBasicJavaColorSettingsPage implements RainbowColorSettingsPage{

  @Override
  public Icon getIcon() {
    return JavaFileType.INSTANCE.getIcon();
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
