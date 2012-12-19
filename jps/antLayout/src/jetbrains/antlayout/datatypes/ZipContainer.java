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
import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.BuildException;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * @author max
 */
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

        return Arrays.asList(result);
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

    public void validateArguments() throws BuildException {
        super.validateArguments();

        if (name == null) {
            throw new BuildException("name attribute must be specified for zipentry or jarentry");
        }
    }
}
