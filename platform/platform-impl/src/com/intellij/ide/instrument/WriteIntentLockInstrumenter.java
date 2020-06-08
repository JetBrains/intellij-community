// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.instrument;

import com.intellij.ide.plugins.MainRunner;
import com.intellij.openapi.diagnostic.Logger;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.jetbrains.org.objectweb.asm.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public final class WriteIntentLockInstrumenter {

  private static final Set<String> ourAlreadyInstrumented = Collections.synchronizedSet(new HashSet<>());
  public static final Object LOCK = new Object();
  public static final String DIRTY_UI_SIGNATURE = "Lcom/intellij/ui/DirtyUI;";

  public static void instrument() {
    Instrumentation instrumentation = ByteBuddyAgent.install();

    synchronized (LOCK) {
      instrumentation.addTransformer(new ClassFileTransformer() {
        @Override
        public byte[] transform(ClassLoader loader,
                                String className,
                                Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain,
                                byte[] classfileBuffer) {
          //it is possible for synthetic classes generated for lambdas
          if (className == null || loader == null || ourAlreadyInstrumented.contains(className)) {
            return null;
          }

          boolean[] shouldProcess = {false};
          Set<String> methodsToAnnotate = new HashSet<>();
          ClassReader cr = new ClassReader(classfileBuffer);
          cr.accept(new ClassVisitor(Opcodes.API_VERSION) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
              if (descriptor.equals(DIRTY_UI_SIGNATURE)) {
                shouldProcess[0] = true;
              }
              return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
              return new MethodVisitor(api) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                  if (descriptor.equals(DIRTY_UI_SIGNATURE)) {
                    shouldProcess[0] = true;
                    methodsToAnnotate.add(name);
                  }
                  return null;
                }
              };
            }
          }, 7);

          if (!shouldProcess[0]) {
            if (Stream
              .concat(Stream.of(cr.getSuperName()), Stream.of(cr.getInterfaces())).noneMatch(name -> ourAlreadyInstrumented.contains(name))) {
              return null;
            }
          }

          ourAlreadyInstrumented.add(className);

          Logger.getInstance(MainRunner.class).info("Redefining " + className);
          ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
          cr.accept(new LockWrappingClassVisitor(cw, className, methodsToAnnotate), ClassReader.SKIP_FRAMES);
          return cw.toByteArray();
        }
      }, true);
    }

  }
}
