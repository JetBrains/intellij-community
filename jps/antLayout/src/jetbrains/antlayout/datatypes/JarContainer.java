// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package jetbrains.antlayout.datatypes;

import org.apache.tools.ant.Main;
import org.apache.tools.ant.taskdefs.*;
import org.apache.tools.ant.types.EnumeratedAttribute;
import org.apache.tools.ant.types.ZipFileSet;

import java.io.File;

public class JarContainer extends ZipContainer {
  @Override
  protected Zip createTask() {
    String version = Main.getAntVersion();
    Jar task;
    if (version != null && (version.contains("1.8.0") || version.contains("1.8.1"))) {
      task = new PatchedJar();
    }
    else {
      task = new Jar();
    }
    task.setTaskName("jar");
    task.setWhenmanifestonly((Zip.WhenEmpty)EnumeratedAttribute.getInstance(Zip.WhenEmpty.class, "skip"));
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

  public void setWhenmanifestonly(Zip.WhenEmpty we) {
    ((Jar)task).setWhenmanifestonly(we);
  }

  public void addConfiguredManifest(Manifest newManifest) throws ManifestException {
    ((Jar) task).addConfiguredManifest(newManifest);
  }

  public void addMetainf(ZipFileSet fs) {
    ((Jar) task).addMetainf(fs);
  }
}
