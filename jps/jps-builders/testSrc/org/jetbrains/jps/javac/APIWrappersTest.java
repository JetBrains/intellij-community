// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac;

import junit.framework.TestCase;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class APIWrappersTest extends TestCase {

  public void testWrapperMethodsMatchAPIMethods() {
    checkMethodSignatures(APIWrappers.ProcessingEnvironmentWrapper.class, ProcessingEnvironment.class);
    checkMethodSignatures(APIWrappers.ProcessorWrapper.class, Processor.class);
    checkMethodSignatures(APIWrappers.FilerWrapper.class, Filer.class);
    checkMethodSignatures(APIWrappers.DiagnosticWrapper.class, Diagnostic.class);
    checkMethodSignatures(APIWrappers.DiagnosticListenerWrapper.class, DiagnosticOutputConsumer.class);
  }

  private static <I, W extends APIWrappers.DynamicWrapper<I>> void checkMethodSignatures(Class<W> wrapperClass, Class<I> apiIfaceClass) {
    try {
      Class<?> aClass = wrapperClass;
      while (!(APIWrappers.DynamicWrapper.class.equals(aClass) || Object.class.equals(aClass))) {
        for (Method declaredMethod : aClass.getDeclaredMethods()) {
          if (Modifier.isPublic(declaredMethod.getModifiers())) {
            assertNotNull(apiIfaceClass.getMethod(declaredMethod.getName(), declaredMethod.getParameterTypes()));
          }
          else {
            try {
              final Method apiMethod = apiIfaceClass.getMethod(declaredMethod.getName(), declaredMethod.getParameterTypes());
              fail("Method " + apiMethod.toString() + " in API interface " + apiIfaceClass.getName() + " should not match a non-public method in a wrapper class " + declaredMethod);
            }
            catch (NoSuchMethodException ignored) {
              // as expected: for non-public methods there should be no corresponding methods in the API interface
            }
          }
        }
        aClass = aClass.getSuperclass();
      }
    }
    catch (NoSuchMethodException e) {
      fail("Methods declared in a wrapper class must match methods declared in the corresponding API interface: " + e.getMessage());
    }
  }

}
