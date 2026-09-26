// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package jetbrains.antlayout.datatypes;

import jetbrains.antlayout.util.LayoutFileSet;
import jetbrains.antlayout.util.TempFileFactory;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.Zip;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class ZipContainer extends Container {
    private String name;
    protected Zip task;

    public ZipContainer() {
        task = createTask();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public List<LayoutFileSet> build(TempFileFactory temp) {
        List<LayoutFileSet> built = super.build(temp);
        File dest = temp.allocateTempFile(name);

        task.setProject(getProject());
        task.setDestFile(dest);

        for (LayoutFileSet set : built) {
            task.addZipfileset(set);
        }

        LayoutFileSet result = new LayoutFileSet();
        result.setFile(dest);

        task.perform();

        return Collections.singletonList(result);
    }

    protected Zip createTask() {
        Zip task = new Zip();
        task.setTaskName("zip");
        return task;
    }

    public void setCompress(boolean compress) {
        task.setCompress(compress);
    }

    public void setFilesonly(boolean f) {
        task.setFilesonly(f);
    }

    public void setDuplicate(Zip.Duplicate df) {
        task.setDuplicate(df);
    }

    public void setEncoding(String encoding) {
        task.setEncoding(encoding);
    }

    public void setBasedir(File baseDir) {
        task.setBasedir(baseDir);
    }

    @Override
    public void validateArguments() throws BuildException {
        super.validateArguments();

        if (name == null) {
            throw new BuildException("name attribute must be specified for zipentry or jarentry");
        }
    }
}
