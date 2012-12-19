/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jetbrains.antlayout.datatypes;

import jetbrains.antlayout.util.TempFileFactory;
import jetbrains.antlayout.util.LayoutFileSet;

import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;

/**
 * @author max
 */
public class DirContainer extends Container {
    private String dirName;

    public void setName(String name) {
        dirName = name;
    }

    public List<LayoutFileSet> build(TempFileFactory temp) {
        List<LayoutFileSet> unprefixed = super.build(temp);
        List<LayoutFileSet> prefixed = new ArrayList<LayoutFileSet>();

        for (LayoutFileSet set : unprefixed) {
            LayoutFileSet copy = createCopy(set);
            copy.setPrefix(dirName + "/" + set.getPrefix(getProject()));
            prefixed.add(copy);
        }

        return prefixed;
    }


    public void validateArguments() throws BuildException {
        super.validateArguments();
        if (dirName == null) {
            throw new BuildException("dirname attribute must be specified for direntry");
        }
    }
}
