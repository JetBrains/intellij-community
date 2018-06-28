// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs.pwa;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter;
import org.jetbrains.jps.builders.BuildTargetIndex;
import org.jetbrains.jps.builders.BuildTargetRegistry;
import org.jetbrains.jps.builders.ModuleBasedTarget;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.instrumentation.BaseInstrumentingBuilder;
import org.jetbrains.jps.incremental.messages.CustomBuilderMessage;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.org.objectweb.asm.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;

public class PwaBuilder extends BaseInstrumentingBuilder {

  @Override
  public void buildStarted(CompileContext context) {
    PwaIndexWriter.initialize(context);
  }

  @Override
  protected boolean canInstrument(CompiledClass compiledClass, int classFileVersion) {
    return true;
  }

  @Nullable
  @Override
  protected BinaryContent instrument(CompileContext context,
                                     CompiledClass compiled,
                                     ClassReader reader,
                                     ClassWriter writer,
                                     InstrumentationClassFinder finder) {
    PwaIndexWriter indexWriter = PwaIndexWriter.getInstance();
    if (indexWriter == null) {
      return null;
    }

    // it can ba a kotlin file here
    Collection<File> sourceFiles = compiled.getSourceFiles();
    int[] sourceIds = new int[sourceFiles.size()];
    int i = 0;
    for (File file: sourceFiles) {
      try {
        sourceIds[i] = indexWriter.enumeratePath(file.getPath());
        i++;
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    ClassFileData classFileData = new ClassFileData(sourceIds);

    reader.accept(new ClassVisitor(Opcodes.API_VERSION) {
      ClassFileSymbol.Clazz myCurrentClassId;

      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        myCurrentClassId = new ClassFileSymbol.Clazz(indexWriter.enumerateName(name));

        classFileData.defs.put(myCurrentClassId, null);
        ArrayList<ClassFileSymbol.Clazz> clazzes = new ArrayList<>();
        ClassFileSymbol.Clazz superId = new ClassFileSymbol.Clazz(indexWriter.enumerateName(superName));
        clazzes.add(superId);
        for (String i: interfaces) {
          ClassFileSymbol.Clazz interfaceId = new ClassFileSymbol.Clazz(indexWriter.enumerateName(i));
          clazzes.add(interfaceId);
        }

        for (ClassFileSymbol.Clazz clazz: clazzes) {
          classFileData.hierarchyMap.computeIfAbsent(clazz, symbol -> new ArrayList<>()).add(myCurrentClassId);
        }
      }


      @Override
      public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        classFileData.defs.put(new ClassFileSymbol.Field(indexWriter.enumerateName(name), myCurrentClassId.name), null);
        return super.visitField(access, name, desc, signature, value);
      }

      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        ClassFileSymbol.Method myMethod = new ClassFileSymbol.Method(indexWriter.enumerateName(name), myCurrentClassId.name, Type.getArgumentTypes(desc).length);
        classFileData.defs.put(myMethod, null);

        return new MethodVisitor(Opcodes.API_VERSION) {
          // todo local variable etc

          @Override
          public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            sink(new ClassFileSymbol.Method(indexWriter.enumerateName(name), indexWriter.enumerateName(owner), Type.getArgumentTypes(desc).length));
          }

          @Override
          public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            sink(new ClassFileSymbol.Field(indexWriter.enumerateName(name), myCurrentClassId.name));
          }

          private void sink(ClassFileSymbol symbol) {
            classFileData.usagesMap.computeIfAbsent(symbol, s -> new ArrayList<>()).add(myMethod);
          }
        };
      }
    }, Opcodes.API_VERSION);

    classFileData.write(indexWriter);
    return null;
  }


  @Override
  public void buildFinished(CompileContext context) {
    PwaIndexWriter.closeIfNeed();
  }

  @Override
  protected boolean isEnabled(CompileContext context, ModuleChunk chunk) {
    return true;
  }

  @Override
  protected String getProgressMessage() {
    return "Analyzing class-files...";
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Class-file analyzer";
  }
}
