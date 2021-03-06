// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.RecordCanBeClassInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SerializableRecordCanBeClassInspectionTest extends LightJavaInspectionTestCase {

  public void testSerializableVersionUIDWithoutSerial() {
    doTest("import java.io.Serializable;\n" +
           "record <warning descr=\"Record can be converted to a class\"><caret>R</warning>() implements Serializable {\n" +
           "  @MyAnn\n" +
           "  private static final long serialVersionUID = 1;\n" +
           "  static long number = 10;\n" +
           "}");
    checkQuickFix("Convert record to class", "import java.io.Serial;\n" +
                                             "import java.io.Serializable;\n" +
                                             "\n" +
                                             "final class R implements Serializable {\n" +
                                             "    @Serial\n" +
                                             "    @MyAnn\n" +
                                             "    private static final long serialVersionUID = 1;\n" +
                                             "    static long number = 10;\n" +
                                             "\n" +
                                             "    R() {\n" +
                                             "    }\n" +
                                             "\n" +
                                             "    @Override\n" +
                                             "    public boolean equals(Object obj) {\n" +
                                             "        return obj == this || obj != null && obj.getClass() == this.getClass();\n" +
                                             "    }\n" +
                                             "\n" +
                                             "    @Override\n" +
                                             "    public int hashCode() {\n" +
                                             "        return 1;\n" +
                                             "    }\n" +
                                             "\n" +
                                             "    @Override\n" +
                                             "    public String toString() {\n" +
                                             "        return \"R[]\";\n" +
                                             "    }\n" +
                                             "\n" +
                                             "}");
  }

  public void testSerializableVersionUIDWithSerial() {
    doTest("import java.io.Serial;\n" +
           "import java.io.Serializable;\n" +
           "record <warning descr=\"Record can be converted to a class\"><caret>R</warning>() implements Serializable {\n" +
           "  @Serial" +
           "  @MyAnn\n" +
           "  private static final long serialVersionUID = 1;\n" +
           "  static long number = 10;\n" +
           "}");
    checkQuickFix("Convert record to class", "import java.io.Serial;\n" +
                                             "import java.io.Serializable;\n" +
                                             "\n" +
                                             "final class R implements Serializable {\n" +
                                             "    @Serial\n" +
                                             "    @MyAnn\n" +
                                             "    private static final long serialVersionUID = 1;\n" +
                                             "    static long number = 10;\n" +
                                             "\n" +
                                             "    R() {\n" +
                                             "    }\n" +
                                             "\n" +
                                             "    @Override\n" +
                                             "    public boolean equals(Object obj) {\n" +
                                             "        return obj == this || obj != null && obj.getClass() == this.getClass();\n" +
                                             "    }\n" +
                                             "\n" +
                                             "    @Override\n" +
                                             "    public int hashCode() {\n" +
                                             "        return 1;\n" +
                                             "    }\n" +
                                             "\n" +
                                             "    @Override\n" +
                                             "    public String toString() {\n" +
                                             "        return \"R[]\";\n" +
                                             "    }\n" +
                                             "\n" +
                                             "}");
  }

  public void testWithoutSerialVersionUID() {
    doTest("import java.io.Serializable;\n" +
           "record <warning descr=\"Record can be converted to a class\"><caret>R</warning>() implements Serializable {\n" +
           "  static long number = 10;\n" +
           "}");
    checkQuickFix("Convert record to class", "import java.io.Serial;\n" +
                                             "import java.io.Serializable;\n" +
                                             "\n" +
                                             "final class R implements Serializable {\n" +
                                             "    static long number = 10;\n" +
                                             "    @Serial\n" +
                                             "    private static final long serialVersionUID = 0L;\n" +
                                             "\n" +
                                             "    R() {\n" +
                                             "    }\n" +
                                             "\n" +
                                             "    @Override\n" +
                                             "    public boolean equals(Object obj) {\n" +
                                             "        return obj == this || obj != null && obj.getClass() == this.getClass();\n" +
                                             "    }\n" +
                                             "\n" +
                                             "    @Override\n" +
                                             "    public int hashCode() {\n" +
                                             "        return 1;\n" +
                                             "    }\n" +
                                             "\n" +
                                             "    @Override\n" +
                                             "    public String toString() {\n" +
                                             "        return \"R[]\";\n" +
                                             "    }\n" +
                                             "\n" +
                                             "}");
  }

  public void testSerialVersionUIDWithWrongModifier() {
    doTest("import java.io.Serializable;\n" +
           "record <warning descr=\"Record can be converted to a class\"><caret>R</warning>() implements Serializable {\n" +
           "  static long number = 10;\n" +
           "  @MyAnn\n" +
           "  private static long serialVersionUID = 10;\n" + // not final
           "}");
    checkQuickFix("Convert record to class", "import java.io.Serializable;\n" +
                                             "\n" +
                                             "final class R implements Serializable {\n" +
                                             "    static long number = 10;\n" +
                                             "    @MyAnn\n" +
                                             "    private static long serialVersionUID = 10;\n" +
                                             "\n" +
                                             "    R() {\n" +
                                             "    }\n" +
                                             "\n" +
                                             "    @Override\n" +
                                             "    public boolean equals(Object obj) {\n" +
                                             "        return obj == this || obj != null && obj.getClass() == this.getClass();\n" +
                                             "    }\n" +
                                             "\n" +
                                             "    @Override\n" +
                                             "    public int hashCode() {\n" +
                                             "        return 1;\n" +
                                             "    }\n" +
                                             "\n" +
                                             "    @Override\n" +
                                             "    public String toString() {\n" +
                                             "        return \"R[]\";\n" +
                                             "    }\n" +
                                             "\n" +
                                             "}");
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new RecordCanBeClassInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package java.io;\n" +
      "@Target({ElementType.METHOD, ElementType.FIELD})\n" +
      "@Retention(RetentionPolicy.SOURCE)\n" +
      "public @interface Serial {}",

      "@Target({ElementType.FIELD})\n" +
      "@Retention(RetentionPolicy.SOURCE)\n" +
      "public @interface MyAnn {}"
    };
  }
}
