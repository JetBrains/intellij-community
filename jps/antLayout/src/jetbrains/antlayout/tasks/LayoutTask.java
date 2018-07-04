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
package jetbrains.antlayout.tasks;

import jetbrains.antlayout.datatypes.*;
import jetbrains.antlayout.util.TempFileFactory;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class LayoutTask extends Task {
    private final List<Content> containers = new ArrayList<Content>();
    private File destDir;

    public void addDir(DirContainer container) {
        containers.add(container);
    }

    public void addJar(JarContainer container) {
        containers.add(container);
    }

    public void addZip(ZipContainer container) {
        containers.add(container);
    }

    public void addRenamedFile(RenamedFileContainer container) {
        containers.add(container);
    }

    public void addExtractedDir(ExtractedDirContent content) {
        containers.add(content);
    }

    public void addModule(IdeaModule module) {
        containers.add(new FileSetContainer(module));
    }

    public void addModuleTests(IdeaModuleTests module) {
        containers.add(new FileSetContainer(module));
    }

    public void addFileset(FileSet fileSet) {
        containers.add(new FileSetContainer(fileSet));
    }

    public void setTodir(File dir) {
        destDir = dir;
    }

    public File getDestDir() {
        return destDir;
    }

    public void execute() throws BuildException {
        validateArguments();

        RootContainer root = new RootContainer(destDir);
        root.setProject(getProject());
        for (Content content : containers) {
            root.addContent(content);
        }

        final File tempDir = new File(destDir, "___tmp___");
        tempDir.mkdirs();

        root.build(new TempFileFactory() {
            int counter = 0;
            public File allocateTempFile(String name) {
                File localTmp = new File(tempDir, "_" + counter + "/");
                counter++;
                localTmp.mkdirs();
                return new File(localTmp, name);
            }
        });

        deleteRecursively(tempDir);
    }

    protected void deleteRecursively(File d) {
        if (d.isDirectory()) {
            for (File file : d.listFiles()) {
                deleteRecursively(file);
            }
        }

        if (!d.delete()) {
            throw new BuildException("Unable to delete file " + d.getAbsolutePath());
        }
    }

    private void validateArguments() throws BuildException {
        if (destDir == null) {
            throw new BuildException("todir attribute must be specified");
        }

        for (Content content : containers) {
            content.validateArguments();
        }
    }
}
