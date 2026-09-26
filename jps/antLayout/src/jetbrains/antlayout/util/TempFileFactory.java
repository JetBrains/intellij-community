// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package jetbrains.antlayout.util;

import java.io.File;

public interface TempFileFactory {
    File allocateTempFile(String name);
}
