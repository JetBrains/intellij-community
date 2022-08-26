// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.internal.statistic.utils.DumpLaunchParametersStarter;

import java.io.IOException;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws IOException {
        new DumpLaunchParametersStarter().premain(Arrays.stream(args).toList());
    }
}

