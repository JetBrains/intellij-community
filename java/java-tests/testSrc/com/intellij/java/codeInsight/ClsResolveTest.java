// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.testFramework.rules.TempDirectory;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.StubMethod;
import net.bytebuddy.jar.asm.Opcodes;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;

public class ClsResolveTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  private JavaCodeInsightTestFixture myFixture;

  @Before
  public void setUp() throws Exception {
    Path jar = generateMutantJar();
    DefaultLightProjectDescriptor descriptor = new DefaultLightProjectDescriptor() {
      @Override
      public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
        super.configureModule(module, model, contentEntry);
        PsiTestUtil.addLibrary(model, "mutant", jar.getParent().toString(), jar.getFileName().toString());
      }
    };
    TestFixtureBuilder<IdeaProjectTestFixture> builder = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder(descriptor);
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(builder.getFixture(), new LightTempDirTestFixtureImpl(true));
    myFixture.setUp();
  }

  //<editor-fold desc="Test data generation">
  private Path generateMutantJar() {
    try {
      Path file = tempDir.newFile("mutant.jar").toPath();
      try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(file))) {
        writeEntry(jar, "mutant1/aB.class", classBytes("mutant1.aB", "m1"));
        writeEntry(jar, "mutant1/ab.class", classBytes("mutant1.ab", "m2"));
        writeEntry(jar, "MUTANT2/C.class", classBytes("MUTANT2.C", "m3"));
        writeEntry(jar, "mutant2/D.class", classBytes("mutant2.D", "m4"));
      }
      return file;
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void writeEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
    ZipEntry entry = new ZipEntry(name);
    zip.putNextEntry(entry);
    zip.write(data);
    zip.closeEntry();
  }

  private static byte[] classBytes(String name, String method) {
    return new ByteBuddy()
      .subclass(Object.class).modifiers(Opcodes.ACC_PUBLIC).name(name)
      .defineMethod(method, void.class, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC).intercept(StubMethod.INSTANCE)
      .make().getBytes();
  }
  //</editor-fold>

  @After
  public void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
  }

  @Test
  public void resolveIntoMutantJar() {
    runInEdtAndWait(() -> {
      myFixture.configureByText(
        "test.java",
        "class test {{\n" +
        "  mutant1.aB.m1();\n" +
        "  mutant1.ab.m2();\n" +
        "  MUTANT2.C.m3();\n" +
        "  mutant2.D.m4();\n" +
        "}}");
      myFixture.checkHighlighting();
    });
  }
}