package org.jetbrains.debugger.memory.sizeof;

import com.sun.jdi.*;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class RetainedSizeFetcher extends SizeOfEvalStrategy {
  private final static long REFERENCE_SIZE = 4;
  private final static long CHAR_SIZE = 2;
  private final static long INT_SIZE = 4;
  private final static long FLOAT_SIZE = 4;
  private final static long DOUBLE_SIZE = 8;
  private final static long BYTE_SIZE = 1;
  private final static long BOOLEAN_SIZE = 1; // ?
  private final static long LONG_SIZE = 8;
  private final static long SHORT_SIZE = 2;

  private final boolean myCheckReferringObjects;

  public RetainedSizeFetcher(boolean checkReferringObjects) {
    myCheckReferringObjects = checkReferringObjects;
  }

  private static long getPrimitiveTypeSize(PrimitiveValue value) {
    if (value instanceof IntegerValue) {
      return INT_SIZE;
    }
    if (value instanceof LongValue) {
      return LONG_SIZE;
    }
    if (value instanceof DoubleValue) {
      return DOUBLE_SIZE;
    }
    if (value instanceof FloatValue) {
      return FLOAT_SIZE;
    }
    if (value instanceof CharValue) {
      return CHAR_SIZE;
    }
    if (value instanceof ByteValue) {
      return BYTE_SIZE;
    }
    if (value instanceof BooleanValue) {
      return BOOLEAN_SIZE;
    }

    return SHORT_SIZE;
  }

  private long getValueSize(Value value, Collection<ObjectReference> visited) {
    if (value == null) {
      return 0;
    }

    if (value instanceof PrimitiveValue) {
      return getPrimitiveTypeSize((PrimitiveValue) value);
    } else {
      ObjectReference ref = (ObjectReference) value;
      if (visited.contains(ref)) {
        return 0;
      }

      if (myCheckReferringObjects) {
        long startTime = System.nanoTime();
        int needReferringObjectCount = visited.size() + 1;
        Collection<ObjectReference> referringObjects = ref.referringObjects(needReferringObjectCount);
        long duration = System.nanoTime() - startTime;
        System.out.println("referringObjects(" + needReferringObjectCount + ") duration = " + duration + "ns "+
            "(" + TimeUnit.NANOSECONDS.toMillis(duration) + " ms).");
        if (referringObjects.stream().anyMatch(objectReference -> !visited.contains(objectReference))) {
          return 0;
        }
      }

      return sizeOfImpl(ref, visited, false);
    }
  }

  @Override
  protected long sizeOfImpl(ObjectReference ref, Collection<ObjectReference> visited, boolean isRoot) {
    visited.add(ref);
    long result = 0;

    if (ref instanceof ArrayReference) {
      ArrayReference arrRef = (ArrayReference) ref;
      int length = arrRef.length();
      for (int i = 0; i < length; i++) {
        result += getValueSize(arrRef.getValue(i), visited);
      }
    }

    for (Field field : ref.referenceType().allFields()) {
      Value val = ref.getValue(field);
      if (field.isStatic()) {
        continue;
      }

      result += REFERENCE_SIZE;
      if (val == null) {
        continue;
      }

      result += getValueSize(val, visited);
    }

    return result;
  }
}
