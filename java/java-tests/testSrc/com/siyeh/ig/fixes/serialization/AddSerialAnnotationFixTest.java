// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.serialization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.serialization.MissingSerialAnnotationInspection;
import org.jetbrains.annotations.Nullable;

public class AddSerialAnnotationFixTest extends LightJavaInspectionTestCase {

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      """
package java.io;
import java.lang.annotation.*;
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
public @interface Serial {}"""
    };
  }

  public void testAdditionToField() {
    doTest("""
             import java.io.*;
             class Test implements Serializable {
               private static final long /*'serialVersionUID' can be annotated with '@Serial' annotation*//*_*/serialVersionUID/**/ = 7874493593505141603L;
             }""");
    checkQuickFix("Annotate field 'serialVersionUID' as '@Serial'", """
      import java.io.*;
      class Test implements Serializable {
        @Serial
        private static final long serialVersionUID = 7874493593505141603L;
      }""");
  }

  public void testAdditionToMethod() {
    doTest("""
             import java.io.*;
             class Test implements Serializable {
               protected Object /*'readResolve()' can be annotated with '@Serial' annotation*//*_*/readResolve/**/() throws ObjectStreamException {
                 return 1;
               }
             }""");
    checkQuickFix("Annotate method 'readResolve()' as '@Serial'",
                  """
                    import java.io.*;
                    class Test implements Serializable {
                      @Serial
                      protected Object readResolve() throws ObjectStreamException {
                        return 1;
                      }
                    }""");
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new MissingSerialAnnotationInspection();
  }
}
