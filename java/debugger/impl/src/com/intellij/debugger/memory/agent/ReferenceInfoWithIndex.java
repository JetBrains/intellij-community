// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.FieldReferringObject;
import com.intellij.debugger.engine.ReferringObject;
import com.intellij.debugger.engine.SimpleReferringObject;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.ListIterator;

public class ReferenceInfoWithIndex extends MemoryAgentReferenceInfo {
  @Nullable private Field field;

  public ReferenceInfoWithIndex(@NotNull ObjectReference referrer,
                                @NotNull ReferenceKind kind,
                                int fieldIndex) {
    super(referrer, kind);
    this.field = getFieldByJVMTIFieldIndex(referrer, fieldIndex);
  }

  private static @Nullable Field getFieldByJVMTIFieldIndex(ObjectReference reference, int index) {
    if (index < 0) {
      return null;
    }

    List<Field> allFields = reference.referenceType().allFields();
    ListIterator<Field> it = allFields.listIterator(allFields.size());
    ReferenceType declaringType = null;
    while (it.hasPrevious()) {
      Field field = it.previous();
      if (!field.declaringType().equals(declaringType)) {
        declaringType = field.declaringType();
        List<Field> fields = field.declaringType().fields();
        if (index < fields.size()) {
          return fields.get(index);
        }
        index -= fields.size();
      }
    }

    return null;
  }

  @NotNull
  @Override
  public ReferringObject createReferringObject() {
    return field == null ?
      new SimpleReferringObject(referrer) :
      new FieldReferringObject(referrer, field);
  }
}
