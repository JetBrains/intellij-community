// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;

import java.util.List;

public class FinalUtilsTest extends LightJavaCodeInsightTestCase {

  public void testCanBeFinal() {
    String file = """
      class A {
          private final int field1 = 0;
          private int field2 = 0;
          private int field3 = 0;
          final int field4 = 0;
          int field5 = 0;
          private int field6;
          private int field7;
          private int field8;
          private int field9;
          private int field10;
          private int field11;
            
          A(int p1, int p2, A a) {
              field6 = p1;
              field7 = 10;
              this.field8 = p2;
              this.field9 = 10;
              if (p1 > 0) {
                  this.field10 = 10;
              }
              a.field11 = 10;
          }
            
          void foo() {
              field3 = field2;
          }
      }
      """;
    PsiJavaFile
      javaFile = (PsiJavaFile)PsiFileFactory.getInstance(getProject()).createFileFromText("X.java", JavaFileType.INSTANCE, file);
    List<String> immutableFields = List.of("field1", "field2", "field4", "field5", "field6", "field7", "field8", "field9");
    PsiField[] fields = javaFile.getClasses()[0].getFields();
    for (PsiField field : fields) {
      assertEquals(immutableFields.contains(field.getName()), FinalUtils.canBeFinal(field));
    }

  }

  public void testFinalVariable() {
    String file = """
    public class Host {
      private String name;
  
      public String getName() {
        return "Name";
      }
    }
      """;
    PsiJavaFile
      javaFile = (PsiJavaFile)PsiFileFactory.getInstance(getProject()).createFileFromText("X.java", JavaFileType.INSTANCE, file);
    PsiField[] fields = javaFile.getClasses()[0].getFields();
    for (PsiField field : fields) {
      if (field.getName().equals("name")) {
        assertFalse(FinalUtils.canBeFinal(field));
      }
    }

  }
}