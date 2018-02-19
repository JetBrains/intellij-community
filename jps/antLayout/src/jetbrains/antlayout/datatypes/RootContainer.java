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

import jetbrains.antlayout.util.LayoutFileSet;
import jetbrains.antlayout.util.TempFileFactory;
import org.apache.tools.ant.taskdefs.Copy;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class RootContainer extends Container {
    private final File destDirectory;

    public RootContainer(File destDirectory) {
        this.destDirectory = destDirectory;
    }


    public List<LayoutFileSet> build(TempFileFactory temp) {
        List<LayoutFileSet> built = super.build(temp);
        for (LayoutFileSet set : built) {
            copySet(set);
        }

        return Collections.emptyList();
    }

    private void copySet(LayoutFileSet set) {
        Copy task = new Copy();
        task.setTaskName("copy");
        task.setProject(getProject());
        String prefix = set.getPrefix(getProject());
        File target = prefix.length() > 0 ? new File(destDirectory, prefix + "/") : destDirectory;

        target.mkdirs();

        task.setTodir(target);
        LayoutFileSet unprefixed = (LayoutFileSet) set.clone();
        unprefixed.setPrefix("");

        task.addFileset(unprefixed);
        task.perform();
    }
}
