/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.compiler.notNullVerification;

import com.intellij.JavaTestUtil;
import com.intellij.compiler.PsiClassWriter;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.List;

/**
 * @author yole
 */
public class NotNullVerifyingInstrumenterTest extends UsefulTestCase {
  private boolean myJava6;
  private IdeaProjectTestFixture myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final JavaTestFixtureFactory fixtureFactory = JavaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = fixtureFactory.createLightFixtureBuilder();
    myFixture = testFixtureBuilder.getFixture();
    myFixture.setUp();
    myJava6 = SystemInfo.isJavaVersionAtLeast("1.6");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    finally {
      super.tearDown();
    }
  }

  public void testSimpleReturn() throws Exception {
    Class<?> testClass = prepareTest();
    Object instance = testClass.newInstance();
    Method method = testClass.getMethod("test");
    verifyCallThrowsException("@NotNull method SimpleReturn.test must not return null", instance, method);
  }

  public void testSimpleReturnWithMessage() throws Exception {
    Class<?> testClass = prepareTest();
    Object instance = testClass.newInstance();
    Method method = testClass.getMethod("test");
    verifyCallThrowsException("This method cannot return null", instance, method);
  }

  public void testMultipleReturns() throws Exception {
    Class<?> testClass = prepareTest();
    Object instance = testClass.newInstance();
    Method method = testClass.getMethod("test", int.class);
    verifyCallThrowsException("@NotNull method MultipleReturns.test must not return null", instance, method, 1);
  }

  public void testSimpleParam() throws Exception {
    Class<?> testClass = prepareTest();
    Object instance = testClass.newInstance();
    Method method = testClass.getMethod("test", Object.class);
    verifyCallThrowsException("Argument 0 for @NotNull parameter of SimpleParam.test must not be null", instance, method, (Object)null);
  }

  public void testSimpleParamWithMessage() throws Exception {
    Class<?> testClass = prepareTest();
    Object instance = testClass.newInstance();
    Method method = testClass.getMethod("test", Object.class);
    verifyCallThrowsException("SimpleParamWithMessage.test(o) cant be null", instance, method, (Object)null);
  }

  public void testConstructorParam() throws Exception {
    Class<?> testClass = prepareTest();
    Constructor method = testClass.getConstructor(Object.class);
    verifyCallThrowsException("Argument 0 for @NotNull parameter of ConstructorParam.<init> must not be null", null, method, (Object)null);
  }

  public void testConstructorParamWithMessage() throws Exception {
    Class<?> testClass = prepareTest();
    Constructor method = testClass.getConstructor(Object.class);
    verifyCallThrowsException("ConstructorParam.ConstructorParam.o cant be null", null, method, (Object)null);
  }

  public void testUseParameterNames() throws Exception {
    Class<?> testClass = prepareTest(true);
    Constructor constructor = testClass.getConstructor(Object.class, Object.class);
    verifyCallThrowsException("Argument for @NotNull parameter 'obj2' of UseParameterNames.<init> must not be null", null, constructor, null, null);

    Method staticMethod = testClass.getMethod("staticMethod", Object.class);
    verifyCallThrowsException("Argument for @NotNull parameter 'y' of UseParameterNames.staticMethod must not be null", null, staticMethod, (Object)null);

    Object instance = constructor.newInstance("", "");
    Method instanceMethod = testClass.getMethod("instanceMethod", Object.class);
    verifyCallThrowsException("Argument for @NotNull parameter 'x' of UseParameterNames.instanceMethod must not be null", instance, instanceMethod, (Object)null);
  }

  public void testLongParameter() throws Exception {
    Class<?> testClass = prepareTest(true);
    Method staticMethod = testClass.getMethod("foo", long.class, String.class, String.class);
    verifyCallThrowsException("Argument for @NotNull parameter 'c' of LongParameter.foo must not be null", null, staticMethod, new Long(2), "z", null);
  }

  public void testDoubleParameter() throws Exception {
    Class<?> testClass = prepareTest(true);
    Method staticMethod = testClass.getMethod("foo", double.class, String.class, String.class);
    verifyCallThrowsException("Argument for @NotNull parameter 'c' of DoubleParameter.foo must not be null", null, staticMethod, new Long(2), "z", null);
  }

  public void testEnumConstructor() throws Exception {
    Class testClass = prepareTest();
    Object field = testClass.getField("Value");
    assertNotNull(field);
  }

  public void testCustomExceptionType() throws Exception {
    Class<?> testClass = prepareTest();
    try {
      testClass.getMethod("foo", Object.class, Object.class).invoke(testClass.newInstance(), null, null);
      fail();
    }
    catch (InvocationTargetException e) {
      //noinspection ThrowableResultOfMethodCallIgnored
      assertInstanceOf(e.getCause(), NullPointerException.class);
      assertEquals("Argument 1 for @NotNull parameter of CustomExceptionType.foo must not be null", e.getCause().getMessage());
    }
  }

  public void testEnumConstructorSecondParam() throws Exception {
    Class testClass = prepareTest();
    Object field = testClass.getField("Value");
    assertNotNull(field);
  }

  public void testStaticInnerClass() throws Exception {
    final Class aClass = prepareTest();
    assertNotNull(aClass.newInstance());
  }

  public void testNonStaticInnerClass() throws Exception {
    final Class aClass = prepareTest();
    assertNotNull(aClass.newInstance());
  }

  private static void verifyCallThrowsException(String expectedError, @Nullable Object instance, Member member, @Nullable Object... args) throws Exception {
    String exceptionText = null;
    try {
      if (member instanceof Constructor) {
        ((Constructor)member).newInstance(args);
      }
      else {
        ((Method)member).invoke(instance, args);
      }
    }
    catch(InvocationTargetException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof IllegalStateException || cause instanceof IllegalArgumentException) {
        exceptionText = cause.getMessage();
      }
    }
    assertEquals(expectedError, exceptionText);
  }

  private Class prepareTest() throws IOException {
    return prepareTest(false);
  }
  
  private Class prepareTest(boolean withDebugInfo) throws IOException {
    String base = JavaTestUtil.getJavaTestDataPath() + "/compiler/notNullVerification/";
    final String baseClassName = getTestName(false);
    String path = base + baseClassName;
    String javaPath = path + ".java";
    File classesDir = FileUtil.createTempDirectory(baseClassName, "output");

    try {
      List<String> cmdLine = ContainerUtil.newArrayList("-classpath", base + "annotations.jar", "-d", classesDir.getAbsolutePath());
      if (withDebugInfo) {
        cmdLine.add("-g");
      }
      cmdLine.add(javaPath);
      com.sun.tools.javac.Main.compile(ArrayUtil.toStringArray(cmdLine));

      Class mainClass = null;
      final File[] files = classesDir.listFiles();
      assertNotNull(files);
      boolean modified = false;
      MyClassLoader classLoader = new MyClassLoader(getClass().getClassLoader());
      for (File file : files) {
        final String fileName = file.getName();
        byte[] content = FileUtil.loadFileBytes(file);

        ClassReader reader = new ClassReader(content, 0, content.length);
        ClassWriter writer = new PsiClassWriter(myFixture.getProject(), myJava6);
        modified |= NotNullVerifyingInstrumenter.processClassFile(reader, writer);

        byte[] instrumented = writer.toByteArray();
        final String className = FileUtil.getNameWithoutExtension(fileName);
        final Class aClass = classLoader.doDefineClass(className, instrumented);
        if (className.equals(baseClassName)) {
          mainClass = aClass;
        }
      }
      assertTrue(modified);
      assertNotNull("Class " + baseClassName + " not found!", mainClass);
      return mainClass;
    }
    finally {
      FileUtil.delete(classesDir);
    }
  }

  private static class MyClassLoader extends ClassLoader {
    public MyClassLoader(ClassLoader parent) {
      super(parent);
    }

    public Class doDefineClass(String name, byte[] data) {
      return defineClass(name, data, 0, data.length);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
      return super.loadClass(name);
    }
  }
}
