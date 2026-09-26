// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.AnnotationVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.FieldVisitor;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.RecordComponentVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Verifies that bytecode annotations whose type descriptor carries a bad character
 * (synthetic JDK markers like {@code Ljdk/Profile+Annotation;} are silently skipped.
 * Such names are legal in the classfile but not a
 * valid Java reference, so letting them through produces
 * {@code IncorrectOperationException} when the stub text is later re-parsed.
 */
public class StubBuildingVisitorInvalidAnnotationTest extends LightIdeaTestCase {
  private static final String PLUS_DESC = "Ljdk/Profile+Annotation;";
  private static final String MINUS_DESC = "Lorg/some-test/Annotation;";
  private static final String DEPRECATED_DESC = "Ljava/lang/Deprecated;";

  public void testPlusAnnotationsAreDroppedAtEveryLevel() throws Exception {
    doTest(PLUS_DESC, "+");
  }

  public void testMinusAnnotationsAreDroppedAtEveryLevel() throws Exception {
    doTest(MINUS_DESC, "-");
  }

  private static void doTest(String desc, String s) throws ClsFormatException {
    byte[] bytes = generateClassBytes(desc);

    List<String> loggedErrors = new ArrayList<>();
    List<String> annotationTexts = new ArrayList<>();
    LoggedErrorProcessor.executeWith(
      new LoggedErrorProcessor() {
        @Override
        public @NotNull Set<Action> processError(@NotNull String category,
                                                 @NotNull String message,
                                                 String @NotNull [] details,
                                                 Throwable t) {
          loggedErrors.add(message);
          return Action.NONE;
        }
      },
      () -> annotationTexts.addAll(collectAnnotationTexts(buildStub(bytes))));

    assertEmpty(loggedErrors);

    for (String text : annotationTexts) {
      assertFalse(text.contains(s));
    }

    assertTrue(ContainerUtil.exists(annotationTexts, t -> t.startsWith("@java.lang.Deprecated")));
  }

  private static byte[] generateClassBytes(String desc) {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(Opcodes.V16, Opcodes.ACC_PUBLIC, "p/Sample", null, "java/lang/Object", null);

    // class-level: '+' must be dropped, @Deprecated must be kept.
    AnnotationVisitor ca = cw.visitAnnotation(desc, true);
    ca.visit("value", 1);
    ca.visitEnd();
    cw.visitAnnotation(DEPRECATED_DESC, true).visitEnd();

    // field
    FieldVisitor fv = cw.visitField(Opcodes.ACC_PRIVATE, "f", "I", null, null);
    fv.visitAnnotation(desc, true).visitEnd();
    fv.visitEnd();

    // record
    RecordComponentVisitor rcv = cw.visitRecordComponent("r", "I", null);
    rcv.visitAnnotation(desc, true).visitEnd();
    rcv.visitEnd();

    // method + parameter
    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "(I)V", null, null);
    mv.visitAnnotation(desc, true).visitEnd();
    mv.visitParameterAnnotation(0, desc, true).visitEnd();
    mv.visitCode();
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(0, 2);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  private static @NotNull PsiJavaFileStub buildStub(byte[] bytes) throws ClsFormatException {
    PsiJavaFileStub stub = ClsFileImpl.buildFileStub(new LightVirtualFile("Sample.class"), bytes);
    assertNotNull(stub);
    return stub;
  }

  private static @NotNull List<String> collectAnnotationTexts(StubElement<?> root) {
    List<String> texts = new ArrayList<>();
    collectRecursively(root, texts);
    return texts;
  }

  private static void collectRecursively(StubElement<?> element, List<String> out) {
    if (element instanceof PsiAnnotationStub) {
      out.add(((PsiAnnotationStub)element).getText());
    }
    for (StubElement<?> child : element.getChildrenStubs()) {
      collectRecursively(child, out);
    }
  }
}
