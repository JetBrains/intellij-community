package jetbrains.antlayout.datatypes;

import org.apache.tools.ant.Main;
import org.apache.tools.ant.taskdefs.*;
import org.apache.tools.ant.types.ZipFileSet;

import java.io.File;

/**
 * @author max
 */
public class JarContainer extends ZipContainer {
  protected Zip createTask() {
    String version = Main.getAntVersion();
    Jar task;
    if (version != null && (version.indexOf("1.8.0") != -1 || version.indexOf("1.8.1") != -1)) {
      task = new PatchedJar();
    }
    else {
      task = new Jar();
    }
    task.setTaskName("jar");
    task.setWhenmanifestonly((Zip.WhenEmpty) Zip.WhenEmpty.getInstance(Zip.WhenEmpty.class, "skip"));
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
