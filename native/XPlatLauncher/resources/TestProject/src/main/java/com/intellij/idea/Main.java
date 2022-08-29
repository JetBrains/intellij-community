// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.internal.statistic.utils.DumpLaunchParametersStarter;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;

public class Main {
  public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
//     how to fail with SIGSEGV:
//
//     Field f = Unsafe.class.getDeclaredField("theUnsafe");
//     f.setAccessible(true);
//     Unsafe unsafe = (Unsafe) f.get(null);
//     unsafe.putAddress(0, 0);

    new DumpLaunchParametersStarter().premain(Arrays.stream(args).toList());
  }
}

