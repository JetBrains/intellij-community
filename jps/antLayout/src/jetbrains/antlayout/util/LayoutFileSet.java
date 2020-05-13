// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package jetbrains.antlayout.util;

import org.apache.tools.ant.types.ZipFileSet;
import org.apache.tools.ant.types.FileSet;

public class LayoutFileSet extends ZipFileSet {
    public LayoutFileSet() {
    }

    public LayoutFileSet(FileSet fileset) {
        super(fileset);
    }

    public LayoutFileSet(ZipFileSet fileset) {
        super(fileset);
    }
}
