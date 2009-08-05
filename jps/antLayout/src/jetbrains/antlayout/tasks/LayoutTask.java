package jetbrains.antlayout.tasks;

import org.apache.tools.ant.Task;
import org.apache.tools.ant.BuildException;
import jetbrains.antlayout.datatypes.*;
import jetbrains.antlayout.util.TempFileFactory;

import java.util.List;
import java.util.ArrayList;
import java.io.File;

/**
 * @author max
 */
public class LayoutTask extends Task {
    private List<Container> containers = new ArrayList<Container>();
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
        for (Container container : containers) {
            root.addContainer(container);
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

        for (Container container : containers) {
            container.validateArguments();
        }
    }
}
