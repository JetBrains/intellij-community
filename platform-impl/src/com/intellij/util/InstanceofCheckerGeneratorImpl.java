/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ConcurrentFactoryMap;
import net.sf.cglib.asm.ClassVisitor;
import net.sf.cglib.asm.Label;
import net.sf.cglib.asm.Type;
import net.sf.cglib.core.*;

import java.lang.reflect.Modifier;

/**
 * @author peter
 */
public class InstanceofCheckerGeneratorImpl extends InstanceofCheckerGenerator {
  private final ConcurrentFactoryMap<Class, Condition<Object>> myCache = new ConcurrentFactoryMap<Class, Condition<Object>>() {
    @Override
    protected Condition<Object> create(final Class key) {
      if (key.isAnonymousClass() || Modifier.isPrivate(key.getModifiers())) {
        return new Condition<Object>() {
          public boolean value(Object o) {
            return key.isInstance(o);
          }
        };
      }

      return new InstanceofClassGenerator(key).createClass();
    }
  };

  public Condition<Object> getInstanceofChecker(final Class<?> someClass) {
    return myCache.get(someClass);
  }

  private static String toInternalName(Class<?> someClass) {
    return someClass.getName().replace('.', '/');
  }

  private static class InstanceofClassGenerator extends AbstractClassGenerator {
    private static final Source SOURCE = new Source("IntellijInstanceof");
    private final Class<?> myCheckedClass;

    public InstanceofClassGenerator(Class<?> checkedClass) {
      super(SOURCE);
      myCheckedClass = checkedClass;
    }

    @Override
    protected ClassLoader getDefaultClassLoader() {
      return myCheckedClass.getClassLoader();
    }

    public Condition<Object> createClass() {
      return (Condition<Object>)super.create(myCheckedClass);
    }

    @Override
    protected Object firstInstance(Class type) throws Exception {
      return type.newInstance();
    }

    @Override
    protected Object nextInstance(Object instance) throws Exception {
      return instance;
    }

    public void generateClass(ClassVisitor classVisitor) throws Exception {
      ClassEmitter cv = new ClassEmitter(classVisitor);

      cv.visit(Constants.V1_2, Modifier.PUBLIC, "com/intellij/util/InstanceofChecker$$$$$" + myCheckedClass.getName().replace('.', '$'), toInternalName(Object.class), new String[]{toInternalName(Condition.class)}, Constants.SOURCE_FILE);
      final Signature signature = new Signature("<init>", "()V");
      final CodeEmitter cons = cv.begin_method(Modifier.PUBLIC, signature, new Type[0], null);
      cons.load_this();
      cons.dup();
      cons.super_invoke_constructor(signature);
      cons.return_value();
      cons.end_method();

      final CodeEmitter e = cv.begin_method(Modifier.PUBLIC, new Signature("value", "(L" + toInternalName(Object.class) + ";)Z"), new Type[0], null);
      e.load_arg(0);
      e.instance_of(Type.getType(myCheckedClass));

      Label fail = e.make_label();
      e.if_jump(CodeEmitter.EQ, fail);
      e.push(true);
      e.return_value();

      e.mark(fail);
      e.push(false);
      e.return_value();
      e.end_method();

      cv.visitEnd();
    }
  }
}
