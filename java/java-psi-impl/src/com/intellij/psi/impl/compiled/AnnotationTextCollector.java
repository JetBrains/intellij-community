// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.AnnotationVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;

final class AnnotationTextCollector extends AnnotationVisitor {
  private final StringBuilder myBuilder = new StringBuilder();
  private final Function<? super String, String> myMapping;
  private final Consumer<? super String> myCallback;
  private boolean hasPrefix;
  private boolean hasParams;

  AnnotationTextCollector(@Nullable String desc, Function<? super String, String> mapping, Consumer<? super String> callback) {
    super(Opcodes.API_VERSION);
    myMapping = mapping;
    myCallback = callback;

    if (desc != null) {
      hasPrefix = true;
      myBuilder.append('@').append(StubBuildingVisitor.toJavaType(Type.getType(desc), myMapping));
    }
  }

  @Override
  public void visit(String name, Object value) {
    valuePairPrefix(name);
    myBuilder.append(StubBuildingVisitor.constToString(value, null, true, myMapping));
  }

  @Override
  public void visitEnum(String name, String desc, String value) {
    valuePairPrefix(name);
    myBuilder.append(StubBuildingVisitor.toJavaType(Type.getType(desc), myMapping)).append('.').append(value);
  }

  private void valuePairPrefix(String name) {
    if (!hasParams) {
      hasParams = true;
      if (hasPrefix) {
        myBuilder.append('(');
      }
    }
    else {
      myBuilder.append(',');
    }

    if (name != null) {
      myBuilder.append(name).append('=');
    }
  }

  @Override
  public AnnotationVisitor visitAnnotation(String name, String desc) {
    valuePairPrefix(name);
    return new AnnotationTextCollector(desc, myMapping, text -> myBuilder.append(text));
  }

  @Override
  public AnnotationVisitor visitArray(String name) {
    valuePairPrefix(name);
    myBuilder.append('{');
    return new AnnotationTextCollector(null, myMapping, text -> myBuilder.append(text).append('}'));
  }

  @Override
  public void visitEnd() {
    if (hasPrefix && hasParams) {
      myBuilder.append(')');
    }
    myCallback.consume(myBuilder.toString());
  }
}
