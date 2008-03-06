/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;

import java.lang.reflect.Method;

public class StubBuilder {
  public void build(PsiElement root) {
    final Class<? extends PsiElement> klass = root.getClass();
    listStubbedMethods(klass);
  }

  public static void listStubbedMethods(final Class<? extends PsiElement> klass) {
    final Method[] methods = klass.getMethods();
    for (Method method : methods) {
      if (method.isAnnotationPresent(Stubbed.class)) {
        System.out.println("Stubbed: " + method.getName());
      }
      else if (method.isAnnotationPresent(MayHaveStubsInside.class)) {
        System.out.println("May have stubs: " + method.getName());
      }
    }
  }
}