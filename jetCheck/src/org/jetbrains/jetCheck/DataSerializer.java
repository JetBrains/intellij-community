// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jetCheck;

import java.io.*;
import java.util.Base64;

/**
 * @author peter
 */
class DataSerializer {

  private static int readINT(ByteArrayInputStream record) {
    int val = readWithEof(record);
    if (val < 192) {
      return val;
    }

    int res = val - 192;
    for (int sh = 6; ; sh += 7) {
      int next = readWithEof(record);
      res |= (next & 0x7F) << sh;
      if ((next & 0x80) == 0) {
        return res;
      }
    }
  }

  private static int readWithEof(ByteArrayInputStream record) {
    if (record.available() <= 0) {
      throw new EOFException();
    }
    return record.read();
  }

  static void writeINT(DataOutput record, int val) throws IOException {
    if (0 > val || val >= 192) {
      record.writeByte(192 + (val & 0x3F));
      val >>>= 6;
      while (val >= 128) {
        record.writeByte((val & 0x7F) | 0x80);
        val >>>= 7;
      }
    }
    record.writeByte(val);
  }
  
  static String serialize(Iteration iteration, StructureElement node) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    try (DataOutputStream data = new DataOutputStream(stream)) {
      writeINT(data, (int)(iteration.iterationSeed >> 32));
      writeINT(data, (int)iteration.iterationSeed);
      writeINT(data, iteration.sizeHint);
      node.serialize(data);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return Base64.getEncoder().encodeToString(stream.toByteArray());
  }

  static void deserializeInto(String data, PropertyChecker.Parameters parameters) {
    ByteArrayInputStream stream = new ByteArrayInputStream(Base64.getDecoder().decode(data));

    int seedHigh = readINT(stream);
    int seedLow = readINT(stream);
    parameters.globalSeed = (long)seedHigh << 32 | seedLow & 0xFFFFFFFFL;

    int hint = readINT(stream);
    parameters.sizeHintFun = __ -> hint;

    parameters.serializedData = (IntDistribution dist) -> {
      int i = readINT(stream);
      if (!dist.isValidValue(i)) {
        throw new CannotRestoreValue("Error restoring from serialized \"rechecking\" data. Possible cause: either the test or the environment it depends on has changed.");
      }
      return i;
    };
  }

  static class EOFException extends RuntimeException {}
}
