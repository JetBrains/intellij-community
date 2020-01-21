// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package jetbrains.antlayout.datatypes;

import jetbrains.antlayout.util.LayoutFileSet;
import jetbrains.antlayout.util.TempFileFactory;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.DataType;

import java.util.List;

public abstract class Content extends DataType {
    public abstract List<LayoutFileSet> build(TempFileFactory temp);

    public abstract void validateArguments() throws BuildException;
}
