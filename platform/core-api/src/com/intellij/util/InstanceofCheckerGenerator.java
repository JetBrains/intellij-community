/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ConcurrentFactoryMap;
import net.sf.cglib.core.*;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;

/**
 * @author peter
 */
public class InstanceofCheckerGenerator {
  private static final InstanceofCheckerGenerator ourInstance;

  static {
    try {
      ClassGenerator.class.getDeclaredMethod("generateClass", ClassVisitor.class);
    }
    catch (NoSuchMethodException e) {
      throw new IllegalStateException("Incorrect cglib version in the classpath, source=" + PathManager.getJarPathForClass(ClassGenerator.class));
    }

    try {
      ourInstance = new InstanceofCheckerGenerator();
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  public static InstanceofCheckerGenerator getInstance() {
    return ourInstance;
  }

  private final ConcurrentFactoryMap<Class, Condition<Object>> myCache = new ConcurrentFactoryMap<Class, Condition<Object>>() {
    @Override
    protected Condition<Object> create(final Class key) {
      if (key.isAnonymousClass() || Modifier.isPrivate(key.getModifiers())) {
        return new Condition<Object>() {
          @Override
          public boolean value(Object o) {
            return key.isInstance(o);
          }
        };
      }

      return new InstanceofClassGenerator(key).createClass();
    }
  };

  @NotNull
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

    @Override
    public void generateClass(ClassVisitor classVisitor) throws Exception {
      ClassEmitter cv = new ClassEmitter(classVisitor);

      cv.visit(Constants.V1_2, Modifier.PUBLIC, "com/intellij/util/InstanceofChecker$$$$$" + myCheckedClass.getName().replace('.', '$'), null, toInternalName(Object.class), new String[]{toInternalName(Condition.class)});
      cv.visitSource(Constants.SOURCE_FILE, null);
      final Signature signature = new Signature("<init>", "()V");
      final CodeEmitter cons = cv.begin_method(Modifier.PUBLIC, signature, new Type[0]);
      cons.load_this();
      cons.dup();
      cons.super_invoke_constructor(signature);
      cons.return_value();
      cons.end_method();

      final CodeEmitter e = cv.begin_method(Modifier.PUBLIC, new Signature("value", "(L" + toInternalName(Object.class) + ";)Z"), new Type[0]);
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
