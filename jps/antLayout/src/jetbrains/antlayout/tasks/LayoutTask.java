// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
 * Assembles files and module outputs accordingly to the specified layout. Used by {@link org.jetbrains.intellij.build.impl.LayoutBuilder}. 
 */
public class LayoutTask extends Task {
    private final List<Content> containers = new ArrayList<>();
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

    @Override
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
