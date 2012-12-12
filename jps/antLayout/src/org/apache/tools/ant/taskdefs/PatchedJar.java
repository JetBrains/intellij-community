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
package org.apache.tools.ant.taskdefs;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.ArchiveFileSet;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ResourceCollection;

import java.io.File;

/**
 * This class is added to workaround https://issues.apache.org/bugzilla/show_bug.cgi?id=49605
 * in Ant 1.8.0 and 1.8.1
 *
 * @author nik
 */
public class PatchedJar extends Jar {
  private static final String MANIFEST_NAME = "META-INF/MANIFEST.MF";

  @Override
  protected ArchiveState getResourcesToAdd(ResourceCollection[] rcs, File zipFile, boolean needsUpdate) throws BuildException {
    if (skipWriting) {
        // this pass is only there to construct the merged
        // manifest this means we claim an update was needed and
        // only include the manifests, skipping any uptodate
        // checks here defering them for the second run
        Resource[][] manifests = grabManifests(rcs);
        int count = 0;
        for (int i = 0; i < manifests.length; i++) {
            count += manifests[i].length;
        }
        log("found a total of " + count + " manifests in "
            + manifests.length + " resource collections",
            Project.MSG_VERBOSE);
        return new ArchiveState(true, manifests);
    }

    return super.getResourcesToAdd(rcs, zipFile, needsUpdate);
  }

  /**
   * This method is copied from Jar class in Ant 1.8.2
   */
  private Resource[][] grabManifests(ResourceCollection[] rcs) {
      Resource[][] manifests = new Resource[rcs.length][];
      for (int i = 0; i < rcs.length; i++) {
          Resource[][] resources = null;
          if (rcs[i] instanceof FileSet) {
              resources = grabResources(new FileSet[] {(FileSet) rcs[i]});
          } else {
              resources = grabNonFileSetResources(new ResourceCollection[] {
                      rcs[i]
                  });
          }
          for (int j = 0; j < resources[0].length; j++) {
              String name = resources[0][j].getName().replace('\\', '/');
              if (rcs[i] instanceof ArchiveFileSet) {
                  ArchiveFileSet afs = (ArchiveFileSet) rcs[i];
                  if (!"".equals(afs.getFullpath(getProject()))) {
                      name = afs.getFullpath(getProject());
                  } else if (!"".equals(afs.getPrefix(getProject()))) {
                      String prefix = afs.getPrefix(getProject());
                      if (!prefix.endsWith("/") && !prefix.endsWith("\\")) {
                          prefix += "/";
                      }
                      name = prefix + name;
                  }
              }
              if (name.equalsIgnoreCase(MANIFEST_NAME)) {
                  manifests[i] = new Resource[] {resources[0][j]};
                  break;
              }
          }
          if (manifests[i] == null) {
              manifests[i] = new Resource[0];
          }
      }
      return manifests;
  }
}
