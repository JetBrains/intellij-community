/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.testFramework;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;

/**
 * @author peter
 */
public class Timings {

  static {
    long start = System.currentTimeMillis();
    BigInteger k = new BigInteger("1");
    for (int i = 0; i < 1000000; i++) {
      k = k.add(new BigInteger("1"));
    }
    for (int i = 0; i < 42; i++) {
      try {
        final File tempFile = File.createTempFile("test", "test" + i);
        final FileWriter writer = new FileWriter(tempFile);
        for (int j = 0; j < 15; j++) {
          writer.write("test" + j);
          writer.flush();
        }
        writer.close();
        final FileReader reader = new FileReader(tempFile);
        while (reader.read() >= 0) {}
        reader.close();
        tempFile.delete();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    MACHINE_TIMING = System.currentTimeMillis() - start;
  }

  public static final long MACHINE_TIMING;


}
