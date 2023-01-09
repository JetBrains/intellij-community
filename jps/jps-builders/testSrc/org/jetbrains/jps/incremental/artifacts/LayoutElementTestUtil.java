// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts;

import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.*;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.JpsModule;

public final class LayoutElementTestUtil {
  public static LayoutElementCreator root() {
    return new LayoutElementCreator(JpsPackagingElementFactory.getInstance().createArtifactRoot(), null);
  }

  public static LayoutElementCreator archive(String name) {
    return new LayoutElementCreator(JpsPackagingElementFactory.getInstance().createArchive(name), null);
  }

  public static void addArtifactToLayout(JpsArtifact main, JpsArtifact included) {
    main.getRootElement().addChild(JpsPackagingElementFactory.getInstance().createArtifactOutput(included.createReference()));
  }

  public static class LayoutElementCreator {
    private final JpsPackagingElementFactory myFactory;
    private final JpsCompositePackagingElement myElement;
    private final LayoutElementCreator myParent;
    
    public LayoutElementCreator(JpsCompositePackagingElement element, LayoutElementCreator parent) {
      myElement = element;
      myParent = parent;
      myFactory = JpsPackagingElementFactory.getInstance();
    }

    public LayoutElementCreator dir(String name) {
      JpsDirectoryPackagingElement dir = myFactory.createDirectory(name);
      myElement.addChild(dir);
      return new LayoutElementCreator(dir, this);
    }

    public LayoutElementCreator archive(String name) {
      JpsArchivePackagingElement archive = myFactory.createArchive(name);
      myElement.addChild(archive);
      return new LayoutElementCreator(archive, this);
    }

    public LayoutElementCreator fileCopy(String filePath) {
      return fileCopy(filePath, null);
    }

    public LayoutElementCreator fileCopy(String filePath, @Nullable String outputFileName) {
      return element(myFactory.createFileCopy(filePath, outputFileName));
    }

    public LayoutElementCreator dirCopy(String dirPath) {
      return element(myFactory.createDirectoryCopy(dirPath));
    }

    public LayoutElementCreator parentDirCopy(String filePath) {
      return dirCopy(PathUtil.getParentPath(filePath));
    }

    public LayoutElementCreator module(JpsModule module) {
      return element(JpsJavaExtensionService.getInstance().createProductionModuleOutput(module.createReference()));
    }

    public LayoutElementCreator moduleSource(JpsModule module) {
      return element(JpsJavaExtensionService.getInstance().createProductionModuleSource(module.createReference()));
    }

    public LayoutElementCreator element(JpsPackagingElement element) {
      myElement.addChild(element);
      return this;
    }

    public LayoutElementCreator lib(JpsLibrary library) {
      return element(myFactory.createLibraryElement(library.createReference()));
    }

    public LayoutElementCreator extractedDir(String jarPath, String pathInJar) {
      return element(myFactory.createExtractedDirectory(jarPath, pathInJar));
    }

    public LayoutElementCreator artifact(JpsArtifact included) {
      return element(myFactory.createArtifactOutput(included.createReference()));
    }

    public LayoutElementCreator end() {
      return myParent;
    }

    public JpsCompositePackagingElement buildElement() {
      if (myParent != null) {
        return myParent.buildElement();
      }
      return myElement;
    }
  }
}
