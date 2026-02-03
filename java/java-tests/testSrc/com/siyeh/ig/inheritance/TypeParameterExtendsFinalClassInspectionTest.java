package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author bas
 */
public class TypeParameterExtendsFinalClassInspectionTest extends LightJavaInspectionTestCase {

  public void testTypeParameterExtendsFinalClass() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new TypeParameterExtendsFinalClassInspection();
  }

  @Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_7;
  }
}
