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
package org.jetbrains.jps.incremental.instrumentation.internal;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.BinaryContent;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.CompiledClass;
import org.jetbrains.jps.incremental.instrumentation.BaseInstrumentingBuilder;
import org.jetbrains.jps.incremental.instrumentation.NotNullInstrumentingBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.org.objectweb.asm.*;
import sun.management.counter.perf.InstrumentationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.org.objectweb.asm.Opcodes.ACC_SYNTHETIC;

/**
 * Adds assertions for method / constructor parameters that are annotated as <code>@SystemDependent</code>.
 * <p>
 * TODO Add other kind of checks (method return, etc).
 */
public class SystemIndependentInstrumentingBuilder extends BaseInstrumentingBuilder {
  private MethodTemplate myAssertionMethodTemplate = new MethodTemplate(AssertionMethodImpl.class);

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
    return NotNullInstrumentingBuilder.isEnabledIn(context);
  }

  @Override
  protected boolean isEnabled(CompileContext context, InstrumentationClassFinder finder) throws IOException {
    return finder.isAvaiable(SystemIndependentInstrumenter.ANNOTATION_CLASS + ".class");
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
    String assertionMethodName = unique("$$$assertArgumentIsSystemIndependent$$$", methodNamesFrom(reader));
    try {
      SystemIndependentInstrumenter instrumenter = new SystemIndependentInstrumenter(writer, assertionMethodName);
      reader.accept(instrumenter, 0);
      if (instrumenter.isAssertionAdded()) {
        myAssertionMethodTemplate.write(writer, ACC_SYNTHETIC, assertionMethodName);
        return new BinaryContent(writer.toByteArray());
      }
    }
    catch (InstrumentationException e) {
      context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, e.getMessage()));
    }
    return null;
  }

  private static List<String> methodNamesFrom(ClassReader reader) {
    List<String> myNames = new ArrayList<>();

    reader.accept(new ClassVisitor(Opcodes.API_VERSION) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        myNames.add(name);
        return null;
      }
    }, 0);

    return myNames;
  }

  private static String unique(String string, List<String> strings) {
    for (int i = 0; i < Integer.MAX_VALUE; i++) {
      String name = i == 0 ? string : string + i;
      if (!strings.contains(name)) {
        return name;
      }
    }
    throw new RuntimeException("Cannot make unique: " + string);
  }
}
