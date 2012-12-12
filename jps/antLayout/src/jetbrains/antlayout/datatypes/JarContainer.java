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
