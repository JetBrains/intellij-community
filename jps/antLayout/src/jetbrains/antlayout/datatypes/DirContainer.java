// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package jetbrains.antlayout.datatypes;

import jetbrains.antlayout.util.LayoutFileSet;
import jetbrains.antlayout.util.TempFileFactory;
import org.apache.tools.ant.BuildException;

import java.util.ArrayList;
import java.util.List;

public class DirContainer extends Container {
    private String dirName;

    public void setName(String name) {
        dirName = name;
    }

    @Override
    public List<LayoutFileSet> build(TempFileFactory temp) {
        List<LayoutFileSet> unprefixed = super.build(temp);
        List<LayoutFileSet> prefixed = new ArrayList<>();

        for (LayoutFileSet set : unprefixed) {
            LayoutFileSet copy = createCopy(set);
            copy.setPrefix(dirName + "/" + set.getPrefix(getProject()));
            prefixed.add(copy);
        }

        return prefixed;
    }


    @Override
    public void validateArguments() throws BuildException {
        super.validateArguments();
        if (dirName == null) {
            throw new BuildException("dirname attribute must be specified for direntry");
        }
    }
}
