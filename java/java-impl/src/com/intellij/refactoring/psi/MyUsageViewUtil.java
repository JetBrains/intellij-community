// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.psi;


public final class MyUsageViewUtil {

    public static String getUsageCountInfo(int usagesCount, int filesCount, String referenceWord) {
        final String info;
        if (filesCount > 0) {
            final String files = filesCount != 1 ? " files " : " file ";
            if (usagesCount > 1) {
                referenceWord += "s";
            }
            info = "( " + usagesCount + " " + referenceWord + " in " + filesCount + files + ")";
        } else {
            info = "( Not found )";
        }
        return info;
    }
}
