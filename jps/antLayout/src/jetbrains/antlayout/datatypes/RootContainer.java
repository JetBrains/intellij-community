// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package jetbrains.antlayout.datatypes;

import jetbrains.antlayout.util.LayoutFileSet;
import jetbrains.antlayout.util.TempFileFactory;
import org.apache.tools.ant.taskdefs.Copy;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class RootContainer extends Container {
    private final File destDirectory;

    public RootContainer(File destDirectory) {
        this.destDirectory = destDirectory;
    }


    @Override
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
        File target = !prefix.isEmpty() ? new File(destDirectory, prefix + "/") : destDirectory;

        target.mkdirs();

        task.setTodir(target);
        LayoutFileSet unprefixed = (LayoutFileSet) set.clone();
        unprefixed.setPrefix("");

        task.addFileset(unprefixed);
        task.perform();
    }
}
