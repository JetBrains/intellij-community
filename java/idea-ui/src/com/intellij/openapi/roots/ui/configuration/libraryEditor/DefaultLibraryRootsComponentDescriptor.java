/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ui.Util;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.AttachRootButtonDescriptor;
import com.intellij.openapi.roots.libraries.ui.ChooserBasedAttachRootButtonDescriptor;
import com.intellij.openapi.roots.libraries.ui.LibraryRootsComponentDescriptor;
import com.intellij.openapi.roots.libraries.ui.OrderRootTypePresentation;
import com.intellij.openapi.roots.ui.OrderRootTypeUIFactory;
import com.intellij.openapi.roots.ui.configuration.PathUIUtils;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class DefaultLibraryRootsComponentDescriptor extends LibraryRootsComponentDescriptor {
  @Override
  public OrderRootTypePresentation getRootTypePresentation(@NotNull OrderRootType type) {
    return getDefaultPresentation(type);
  }

  @NotNull
  @Override
  public List<? extends AttachRootButtonDescriptor> createAttachButtons() {
    return Arrays.asList(new AttachClassesDescriptor(), new AttachJarDirectoriesDescriptor(),
                         new AttachSourcesDescriptor(), new AttachJarSourcesDirectoriesDescriptor(),
                         new AttachAnnotationsDescriptor(), new AttachJavadocDescriptor(),
                         new AttachUrlJavadocDescriptor());
  }

  public static OrderRootTypePresentation getDefaultPresentation(OrderRootType type) {
    final OrderRootTypeUIFactory factory = OrderRootTypeUIFactory.FACTORY.getByKey(type);
    return new OrderRootTypePresentation(factory.getNodeText(), factory.getIcon());
  }

  private static class AttachClassesDescriptor extends ChooserBasedAttachRootButtonDescriptor {
    private AttachClassesDescriptor() {
      super(OrderRootType.CLASSES, ProjectBundle.message("module.libraries.attach.classes.button"));
    }

    public FileChooserDescriptor createChooserDescriptor() {
      return new FileChooserDescriptor(false, true, true, false, false, true);
    }

    public String getChooserTitle(final String libraryName) {
      if (StringUtil.isEmpty(libraryName)) {
        return ProjectBundle.message("library.attach.classes.action");
      }
      else {
        return ProjectBundle.message("library.attach.classes.to.library.action", libraryName);
      }
    }

    public String getChooserDescription() {
      return ProjectBundle.message("library.attach.classes.description");
    }
  }

  private static class AttachJarDirectoriesDescriptor extends ChooserBasedAttachRootButtonDescriptor {
    private AttachJarDirectoriesDescriptor() {
      super(OrderRootType.CLASSES, ProjectBundle.message("module.libraries.attach.jar.directories.button"));
    }

    public FileChooserDescriptor createChooserDescriptor() {
      return new FileChooserDescriptor(false, true, false, false, false, true);
    }

    public boolean addAsJarDirectories() {
      return true;
    }

    public String getChooserTitle(final String libraryName) {
      if (StringUtil.isEmpty(libraryName)) {
        return ProjectBundle.message("library.attach.jar.directory.action");
      }
      else {
        return ProjectBundle.message("library.attach.jar.directory.to.library.action", libraryName);
      }
    }

    public String getChooserDescription() {
      return ProjectBundle.message("library.attach.jar.directory.description");
    }
  }

  private static class AttachJarSourcesDirectoriesDescriptor extends ChooserBasedAttachRootButtonDescriptor {
    private AttachJarSourcesDirectoriesDescriptor() {
      super(OrderRootType.SOURCES, ProjectBundle.message("module.libraries.attach.jar.sources.directories.button"));
    }

    public FileChooserDescriptor createChooserDescriptor() {
      return new FileChooserDescriptor(false, true, false, false, false, true);
    }

    public boolean addAsJarDirectories() {
      return true;
    }

    public String getChooserTitle(final String libraryName) {
      if (StringUtil.isEmpty(libraryName)) {
        return ProjectBundle.message("library.attach.jar.sources.directory.action");
      }
      else {
        return ProjectBundle.message("library.attach.jar.sources.directory.to.library.action", libraryName);
      }
    }

    public String getChooserDescription() {
      return ProjectBundle.message("library.attach.jar.sources.directory.description");
    }
  }

  private static class AttachSourcesDescriptor extends ChooserBasedAttachRootButtonDescriptor {
    private AttachSourcesDescriptor() {
      super(OrderRootType.SOURCES, ProjectBundle.message("module.libraries.attach.sources.button"));
    }

    public String getChooserTitle(final String libraryName) {
      return ProjectBundle.message("library.attach.sources.action");
    }

    public String getChooserDescription() {
      return ProjectBundle.message("library.attach.sources.description");
    }

    @NotNull
    public VirtualFile[] scanForActualRoots(@NotNull final VirtualFile[] rootCandidates, JComponent parent) {
      return PathUIUtils.scanAndSelectDetectedJavaSourceRoots(parent, rootCandidates);
    }
  }

  private static class AttachAnnotationsDescriptor extends ChooserBasedAttachRootButtonDescriptor {
    private AttachAnnotationsDescriptor() {
      super(AnnotationOrderRootType.getInstance(), ProjectBundle.message("attach.annotations.button"));
    }

    public String getChooserTitle(final String libraryName) {
      return ProjectBundle.message("library.attach.external.annotations.action");
    }

    public String getChooserDescription() {
      return ProjectBundle.message("library.attach.external.annotations.description");
    }

    @Override
    public FileChooserDescriptor createChooserDescriptor() {
      return new FileChooserDescriptor(false, true, false, false, false, false);
    }
  }

  private static class AttachJavadocDescriptor extends ChooserBasedAttachRootButtonDescriptor {
    private AttachJavadocDescriptor() {
      super(JavadocOrderRootType.getInstance(), ProjectBundle.message("module.libraries.javadoc.attach.button"));
    }

    public String getChooserTitle(final String libraryName) {
      return ProjectBundle.message("library.attach.javadoc.action");
    }

    public String getChooserDescription() {
      return ProjectBundle.message("library.attach.javadoc.description");
    }
  }

  private static class AttachUrlJavadocDescriptor extends AttachRootButtonDescriptor {
    private AttachUrlJavadocDescriptor() {
      super(JavadocOrderRootType.getInstance(), ProjectBundle.message("module.libraries.javadoc.url.button"));
    }

    @Override
    public VirtualFile[] selectFiles(@NotNull JComponent parent,
                                     @Nullable VirtualFile initialSelection,
                                     @Nullable Module contextModule,
                                     @NotNull LibraryEditor libraryEditor) {
      final VirtualFile vFile = Util.showSpecifyJavadocUrlDialog(parent);
      if (vFile != null) {
        return new VirtualFile[]{vFile};
      }
      return VirtualFile.EMPTY_ARRAY;
    }
  }
}
