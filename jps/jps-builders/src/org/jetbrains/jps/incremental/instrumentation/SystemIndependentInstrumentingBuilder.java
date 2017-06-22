/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.instrumentation;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.BinaryContent;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.CompiledClass;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.org.objectweb.asm.*;
import sun.management.counter.perf.InstrumentationException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adds assertions for method / constructor parameters that are annotated as <code>@SystemDependent</code>.
 * <p>
 * Delegates the check to <code>PathUtil.assertArgumentIsSystemIndependent</code> method.
 * <p>
 * TODO Add other kind of checks (method return, etc).
 */
public class SystemIndependentInstrumentingBuilder extends BaseInstrumentingBuilder {
  // A heuristics to enable the instrumentation only in IDEA Community / IDEA Ultimate projects.
  private static final List<String> IDEA_MODULES = Arrays.asList("openapi", "platform-impl", "idea-ui");
  private static final Key<Boolean> IS_IDEA_PROJECT = Key.create("_is_idea_project_");

  public SystemIndependentInstrumentingBuilder() {
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Path type instrumentation";
  }

  @Override
  protected String getProgressMessage() {
    return "Adding path type assertions...";
  }

  @Override
  protected boolean isEnabled(CompileContext context, ModuleChunk chunk) {
    if (!NotNullInstrumentingBuilder.isEnabledIn(context)) {
      return false;
    }

    if (!IS_IDEA_PROJECT.isIn(context)) {
      Set<String> moduleNames = context.getProjectDescriptor().getProject().getModules().stream()
        .map(JpsModule::getName).collect(Collectors.toSet());

      IS_IDEA_PROJECT.set(context, moduleNames.containsAll(IDEA_MODULES));
    }

    return IS_IDEA_PROJECT.get(context, Boolean.FALSE);
  }

  @Override
  protected boolean canInstrument(CompiledClass compiledClass, int classFileVersion) {
    return !"module-info".equals(compiledClass.getClassName());
  }

  @Nullable
  @Override
  protected BinaryContent instrument(CompileContext context,
                                     CompiledClass compiled,
                                     ClassReader reader,
                                     ClassWriter writer,
                                     InstrumentationClassFinder finder) {
    try {
      reader.accept(new MyClassVisitor(writer), 0);
      return new BinaryContent(writer.toByteArray());
    }
    catch (InstrumentationException e) {
      context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, e.getMessage()));
    }
    return null;
  }


  private static class MyClassVisitor extends ClassVisitor {
    private String myClassName;

    MyClassVisitor(ClassWriter writer) {
      super(Opcodes.API_VERSION, writer);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
      myClassName = name;
      super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
      Type[] argumentTypes = Type.getArgumentTypes(desc);

      Parameter[] parameters = new Parameter[argumentTypes.length];

      int slot = (access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
      for (int i = 0; i < argumentTypes.length; i++) {
        Type argumentType = argumentTypes[i];

        Parameter parameter = new Parameter();
        parameter.name = Integer.toString(i);
        parameter.slot = slot;
        parameter.isString = argumentType.getSort() == Type.OBJECT && "java.lang.String".equals(argumentType.getClassName());

        parameters[i] = parameter;

        slot += argumentType.getSize();
      }

      return new MethodVisitor(api, super.visitMethod(access, name, desc, signature, exceptions)) {
        private int myParameterIndex = 0;

        @Override
        public void visitParameter(String name, int access) {
          parameters[myParameterIndex].name = name;
          myParameterIndex++;
          super.visitParameter(name, access);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
          if (typePath == null) {
            TypeReference ref = new TypeReference(typeRef);
            parameters[ref.getFormalParameterIndex()].isSystemIndependent =
              ref.getSort() == TypeReference.METHOD_FORMAL_PARAMETER && "Lcom/intellij/util/SystemIndependent;".equals(desc);
          }
          return super.visitTypeAnnotation(typeRef, typePath, desc, visible);
        }

        @Override
        public void visitCode() {
          for (Parameter parameter : parameters) {
            if (parameter.isSystemIndependent) {
              if (!parameter.isString) {
                throw new InstrumentationException("Only String can be annotated as @SystemIndependent");
              }
              addAssertionFor(parameter.name, parameter.slot);
            }
          }
          super.visitCode();
        }

        private void addAssertionFor(String parameterName, int parameterSlot) {
          visitLdcInsn(myClassName);
          visitLdcInsn(name);
          visitLdcInsn(parameterName);
          visitVarInsn(Opcodes.ALOAD, parameterSlot);
          visitMethodInsn(Opcodes.INVOKESTATIC,
                          "com/intellij/util/PathUtil",
                          "assertArgumentIsSystemIndependent",
                          "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                          false);
        }
      };
    }

    private static class Parameter {
      String name;
      int slot;
      boolean isString;
      boolean isSystemIndependent;
    }
  }
}
