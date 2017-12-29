package com.intellij.compiler.instrumentation;

import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;

/**
* @author Eugene Zhuravlev
*/
public class InstrumenterClassWriter extends ClassWriter {
  private final InstrumentationClassFinder myFinder;

  public InstrumenterClassWriter(ClassReader reader, int flags, final InstrumentationClassFinder finder) {
    super(reader, flags);
    myFinder = finder;
  }

  public InstrumenterClassWriter(int flags, final InstrumentationClassFinder finder) {
    super(flags);
    myFinder = finder;
  }

  protected String getCommonSuperClass(final String type1, final String type2) {
    try {
      final InstrumentationClassFinder.PseudoClass cls1 = myFinder.loadClass(type1);
      final InstrumentationClassFinder.PseudoClass cls2 = myFinder.loadClass(type2);
      if (cls1.isAssignableFrom(cls2)) {
        return cls1.getName();
      }
      if (cls2.isAssignableFrom(cls1)) {
        return cls2.getName();
      }
      if (cls1.isInterface() || cls2.isInterface()) {
        return "java/lang/Object";
      }
      else {
        InstrumentationClassFinder.PseudoClass c = cls1;
        do {
          c = c.getSuperClass();
        }
        while (!c.isAssignableFrom(cls2));
        return c.getName();
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e.toString(), e);
    }
  }
}
