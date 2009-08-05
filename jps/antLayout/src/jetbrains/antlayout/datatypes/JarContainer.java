package jetbrains.antlayout.datatypes;

import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.taskdefs.Jar;
import org.apache.tools.ant.taskdefs.Manifest;
import org.apache.tools.ant.taskdefs.ManifestException;
import org.apache.tools.ant.types.ZipFileSet;
import org.apache.tools.ant.types.Path;

import java.io.File;

/**
 * @author max
 */
public class JarContainer extends ZipContainer {
    protected Zip createTask() {
        Jar task = new Jar();
        task.setTaskName("jar");
        return task;
    }

    public void setIndex(boolean flag) {
        ((Jar) task).setIndex(flag);
    }

    public void setManifestEncoding(String manifestEncoding) {
        ((Jar) task).setManifestEncoding(manifestEncoding);
    }

    public void setManifest(File manifestFile) {
        ((Jar) task).setManifest(manifestFile);
    }

    public void setFilesetmanifest(Jar.FilesetManifestConfig config) {
        ((Jar) task).setFilesetmanifest(config);
    }
    
    public void addConfiguredManifest(Manifest newManifest) throws ManifestException {
        ((Jar) task).addConfiguredManifest(newManifest);
    }
 
    public void addMetainf(ZipFileSet fs) {
        ((Jar) task).addMetainf(fs);
    }
}
