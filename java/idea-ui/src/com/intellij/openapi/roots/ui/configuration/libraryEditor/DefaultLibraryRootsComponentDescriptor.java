// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ui.Util;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.NativeLibraryOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.*;
import com.intellij.openapi.roots.ui.OrderRootTypeUIFactory;
import com.intellij.openapi.roots.ui.configuration.LibrarySourceRootDetectorUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.util.IconUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class DefaultLibraryRootsComponentDescriptor extends LibraryRootsComponentDescriptor {
  private static final Set<String> NATIVE_LIBRARY_EXTENSIONS =
    new THashSet<>(Arrays.asList("dll", "so", "dylib"), FileUtil.PATH_HASHING_STRATEGY);

  public static final Condition<VirtualFile> LIBRARY_ROOT_CONDITION = file -> FileElement.isArchive(file) || isNativeLibrary(file);

  @Override
  public OrderRootTypePresentation getRootTypePresentation(@NotNull OrderRootType type) {
    return getDefaultPresentation(type);
  }

  @NotNull
  @Override
  public List<? extends AttachRootButtonDescriptor> createAttachButtons() {
    return Collections.singletonList(new AttachUrlJavadocDescriptor());
  }

  @NotNull
  @Override
  public List<? extends RootDetector> getRootDetectors() {
    List<RootDetector> results = new ArrayList<>();
    results.add(new DescendentBasedRootFilter(OrderRootType.CLASSES, false, "classes",
                                              file -> FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE)
                                                      //some libraries store native libraries inside their JAR files and unpack them dynamically so we should detect such JARs as classes roots
                                                      || file.getFileSystem() instanceof JarFileSystem && isNativeLibrary(file)));
    results.add(DescendentBasedRootFilter.createFileTypeBasedFilter(OrderRootType.CLASSES, true, JavaClassFileType.INSTANCE, "jar directory"));
    results.addAll(LibrarySourceRootDetectorUtil.JAVA_SOURCE_ROOT_DETECTOR.getExtensionList());
    results.add(DescendentBasedRootFilter.createFileTypeBasedFilter(OrderRootType.SOURCES, true, JavaFileType.INSTANCE, "source archive directory"));
    results.add(new JavadocRootDetector());
    results.add(createAnnotationsRootDetector());
    results.add(new NativeLibraryRootFilter());
    return results;
  }

  @NotNull
  public static DescendentBasedRootFilter createAnnotationsRootDetector() {
    return new DescendentBasedRootFilter(AnnotationOrderRootType.getInstance(), false, "external annotations",
                                              file -> ExternalAnnotationsManager.ANNOTATIONS_XML.equals(file.getName()));
  }

  private static boolean isNativeLibrary(VirtualFile file) {
    String extension = file.getExtension();
    return extension != null && NATIVE_LIBRARY_EXTENSIONS.contains(extension);
  }

  @NotNull
  @Override
  public FileChooserDescriptor createAttachFilesChooserDescriptor(@Nullable String libraryName) {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, false, true, true).withFileFilter(LIBRARY_ROOT_CONDITION);
    descriptor.setTitle(StringUtil.isEmpty(libraryName) ? ProjectBundle.message("library.attach.files.action")
                                                        : ProjectBundle.message("library.attach.files.to.library.action", libraryName));
    descriptor.setDescription(JavaUiBundle.message("library.java.attach.files.description"));
    return descriptor;
  }

  public static OrderRootTypePresentation getDefaultPresentation(OrderRootType type) {
    final OrderRootTypeUIFactory factory = OrderRootTypeUIFactory.FACTORY.getByKey(type);
    return new OrderRootTypePresentation(factory.getNodeText(), factory.getIcon());
  }

  private static final class JavadocRootDetector extends RootDetector {
    private JavadocRootDetector() {
      super(JavadocOrderRootType.getInstance(), false, "JavaDocs");
    }

    @NotNull
    @Override
    public Collection<VirtualFile> detectRoots(@NotNull VirtualFile rootCandidate, @NotNull ProgressIndicator progressIndicator) {
      List<VirtualFile> result = new ArrayList<>();
      collectJavadocRoots(rootCandidate, result, progressIndicator);
      JavadocQuarantineStatusCleaner.cleanIfNeeded(VfsUtilCore.toVirtualFileArray(result));
      return result;
    }

    private static void collectJavadocRoots(VirtualFile file, final List<? super VirtualFile> result, final ProgressIndicator progressIndicator) {
      VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          progressIndicator.checkCanceled();
          //noinspection SpellCheckingInspection
          if (file.isDirectory() && file.findChild("allclasses-frame.html") != null && file.findChild("allclasses-noframe.html") != null) {
            result.add(file);
            return false;
          }
          return true;
        }
      });
    }
  }

  private static final class NativeLibraryRootFilter extends RootDetector {
    private NativeLibraryRootFilter() {
      super(NativeLibraryOrderRootType.getInstance(), false, "native library location");
    }

    @NotNull
    @Override
    public Collection<VirtualFile> detectRoots(@NotNull VirtualFile rootCandidate, @NotNull ProgressIndicator progressIndicator) {
      if (rootCandidate.isInLocalFileSystem()) {
        if (rootCandidate.isDirectory()) {
          for (VirtualFile file : rootCandidate.getChildren()) {
            if (isNativeLibrary(file)) {
              return Collections.singleton(rootCandidate);
            }
          }
        }
        else if (isNativeLibrary(rootCandidate)) {
          return Collections.singleton(rootCandidate.getParent());
        }
      }
      return Collections.emptyList();
    }
  }

  private static final class AttachUrlJavadocDescriptor extends AttachRootButtonDescriptor {
    private AttachUrlJavadocDescriptor() {
      super(JavadocOrderRootType.getInstance(), IconUtil.getAddLinkIcon(), ProjectBundle.message("module.libraries.javadoc.url.button"));
    }

    @Override
    public VirtualFile[] selectFiles(@NotNull JComponent parent,
                                     @Nullable VirtualFile initialSelection,
                                     @Nullable Module contextModule,
                                     @NotNull LibraryEditor libraryEditor) {
      VirtualFile vFile = Util.showSpecifyJavadocUrlDialog(parent);
      return vFile != null ? new VirtualFile[]{vFile} : VirtualFile.EMPTY_ARRAY;
    }
  }
}