// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
class ClassFileReprExternalizer implements DataExternalizer<ClassFileRepr> {

  private static final byte CLASS = 0x1;
  private static final byte MODULE = 0x2;
  private final DataExternalizer<ClassRepr> myClassReprExternalizer;
  private final DataExternalizer<ModuleRepr> myModuleReprExternalizer;

  public ClassFileReprExternalizer(DependencyContext context) {
    myClassReprExternalizer = ClassRepr.externalizer(context);
    myModuleReprExternalizer = ModuleRepr.externalizer(context);
  }

  public void save(@NotNull DataOutput out, ClassFileRepr value) throws IOException {
    if (value instanceof ClassRepr) {
      out.writeByte(CLASS);
      myClassReprExternalizer.save(out, (ClassRepr)value);
    }
    else {
      out.writeByte(MODULE);
      myModuleReprExternalizer.save(out, (ModuleRepr)value);
    }
  }

  public ClassFileRepr read(@NotNull DataInput in) throws IOException {
    return in.readByte() == CLASS ? myClassReprExternalizer.read(in) : myModuleReprExternalizer.read(in);
  }
}
