package com.intellij.compiler.notNullVerification;

import com.intellij.JavaTestUtil;
import com.intellij.compiler.PsiClassWriter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author yole
 */
public class NotNullVerifyingInstrumenterTest extends UsefulTestCase {
  private IdeaProjectTestFixture myFixture;

  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
  public NotNullVerifyingInstrumenterTest() {
    IdeaTestCase.initPlatformPrefix();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final JavaTestFixtureFactory fixtureFactory = JavaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = fixtureFactory.createLightFixtureBuilder();
    myFixture = testFixtureBuilder.getFixture();
    myFixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    myFixture.tearDown();
    super.tearDown();
  }

  public void testSimpleReturn() throws Exception {
    Class testClass = prepareTest();
    Object instance = testClass.newInstance();
    Method method = testClass.getMethod("test");
    verifyCallThrowsException("@NotNull method SimpleReturn.test must not return null", instance, method);
  }

  public void testMultipleReturns() throws Exception {
    Class testClass = prepareTest();
    Object instance = testClass.newInstance();
    Method method = testClass.getMethod("test", int.class);
    verifyCallThrowsException("@NotNull method MultipleReturns.test must not return null", instance, method, 1);
  }

  public void testEnumConstructor() throws Exception {
    Class testClass = prepareTest();
    Object field = testClass.getField("Value");
    assertNotNull(field);
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

  private static void verifyCallThrowsException(final String expectedError, final Object instance, final Method method, final Object... args) throws IllegalAccessException {
    String exceptionText = null;
    try {
      method.invoke(instance, args);
    }
    catch(InvocationTargetException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof IllegalStateException) {
        exceptionText = cause.getMessage();
      }
    }
    assertEquals(expectedError, exceptionText);
  }

  private Class prepareTest() throws IOException {
    String base = JavaTestUtil.getJavaTestDataPath() + "/compiler/notNullVerification/";
    final String baseClassName = getTestName(false);
    String path = base + baseClassName;
    String javaPath = path + ".java";
    File classesDir = FileUtil.createTempDirectory(baseClassName, "output");

    try {
      com.sun.tools.javac.Main.compile(new String[] { "-classpath", base+"annotations.jar", "-d", classesDir.getAbsolutePath(), javaPath } );

      Class mainClass = null;
      final File[] files = classesDir.listFiles();
      boolean modified = false;
      MyClassLoader classLoader = new MyClassLoader(getClass().getClassLoader());
      for (File file : files) {
        final String fileName = file.getName();
        FileInputStream stream = new FileInputStream(file);
        byte[] content;
        try {
          content = FileUtil.adaptiveLoadBytes(stream);
        }
        finally {
          stream.close();
        }

        ClassReader reader = new ClassReader(content, 0, content.length);
        ClassWriter writer = new PsiClassWriter(myFixture.getProject(), false);
        final NotNullVerifyingInstrumenter instrumenter = new NotNullVerifyingInstrumenter(writer);
        reader.accept(instrumenter, 0);
        modified |= instrumenter.isModification();

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

    public Class<?> loadClass(String name) throws ClassNotFoundException {
      return super.loadClass(name);
    }
  }
}
