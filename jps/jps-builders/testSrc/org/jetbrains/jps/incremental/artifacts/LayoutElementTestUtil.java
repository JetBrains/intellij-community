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
package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.Library;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.artifacts.*;

import java.util.Collections;

/**
 * @author nik
 */
public class LayoutElementTestUtil {
  public static LayoutElementCreator root() {
    return new LayoutElementCreator(new RootElement(Collections.<LayoutElement>emptyList()), null);
  }

  public static LayoutElementCreator archive(String name) {
    return new LayoutElementCreator(new ArchiveElement(name, Collections.<LayoutElement>emptyList()), null);
  }

  public static void addArtifactToLayout(Artifact main, Artifact included) {
    final ArtifactLayoutElement element = new ArtifactLayoutElement();
    element.setArtifactName(included.getName());
    ((CompositeLayoutElement)main.getRootElement()).getChildren().add(element);
  }

  public static class LayoutElementCreator {
    private CompositeLayoutElement myElement;
    private LayoutElementCreator myParent;
    
    public LayoutElementCreator(CompositeLayoutElement element, LayoutElementCreator parent) {
      myElement = element;
      myParent = parent;
    }

    public LayoutElementCreator dir(String name) {
      DirectoryElement dir = new DirectoryElement(name, Collections.<LayoutElement>emptyList());
      myElement.getChildren().add(dir);
      return new LayoutElementCreator(dir, this);
    }

    public LayoutElementCreator archive(String name) {
      ArchiveElement archive = new ArchiveElement(name, Collections.<LayoutElement>emptyList());
      myElement.getChildren().add(archive);
      return new LayoutElementCreator(archive, this);
    }

    public LayoutElementCreator fileCopy(String filePath) {
      return fileCopy(filePath, null);
    }

    public LayoutElementCreator fileCopy(String filePath, @Nullable String outputFileName) {
      myElement.getChildren().add(new FileCopyElement(filePath, outputFileName));
      return this;
    }

    public LayoutElementCreator dirCopy(String dirPath) {
      myElement.getChildren().add(new DirectoryCopyElement(dirPath));
      return this;
    }

    public LayoutElementCreator module(Module module) {
      final ModuleOutputElement element = new ModuleOutputElement();
      element.setModuleName(module.getName());
      myElement.getChildren().add(element);
      return this;
    }

    public LayoutElementCreator lib(Library library) {
      final LibraryFilesElement element = new LibraryFilesElement();
      element.setLibraryName(library.getName());
      element.setLibraryLevel(LibraryFilesElement.PROJECT_LEVEL);
      myElement.getChildren().add(element);
      return this;
    }

    public LayoutElementCreator extractedDir(String jarPath, String pathInJar) {
      ExtractedDirectoryElement dir = new ExtractedDirectoryElement();
      dir.setJarPath(jarPath);
      dir.setPathInJar(pathInJar);
      myElement.getChildren().add(dir);
      return this;
    }

    public LayoutElementCreator artifact(Artifact included) {
      final ArtifactLayoutElement element = new ArtifactLayoutElement();
      element.setArtifactName(included.getName());
      myElement.getChildren().add(element);
      return this;
    }

    public LayoutElementCreator end() {
      return myParent;
    }

    public CompositeLayoutElement buildElement() {
      if (myParent != null) {
        return myParent.buildElement();
      }
      return myElement;
    }
  }
}
