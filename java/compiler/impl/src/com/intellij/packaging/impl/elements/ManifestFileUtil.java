/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.packaging.impl.elements;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.compiler.make.ManifestBuilder;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.PackagingElementPath;
import com.intellij.packaging.impl.artifacts.PackagingElementProcessor;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * @author nik
 */
public class ManifestFileUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorContextImpl");
  public static final String MANIFEST_PATH = JarFile.MANIFEST_NAME;
  public static final String MANIFEST_FILE_NAME = PathUtil.getFileName(MANIFEST_PATH);
  public static final String MANIFEST_DIR_NAME = PathUtil.getParentPath(MANIFEST_PATH);

  private ManifestFileUtil() {
  }

  @Nullable
  public static VirtualFile findManifestFile(@NotNull CompositePackagingElement<?> root, PackagingElementResolvingContext context, ArtifactType artifactType) {
    return ArtifactUtil.findSourceFileByOutputPath(root, MANIFEST_PATH, context, artifactType);
  }

  @Nullable
  public static VirtualFile suggestManifestFileDirectory(@NotNull CompositePackagingElement<?> root, PackagingElementResolvingContext context, ArtifactType artifactType) {
    final VirtualFile metaInfDir = ArtifactUtil.findSourceFileByOutputPath(root, MANIFEST_DIR_NAME, context, artifactType);
    if (metaInfDir != null) {
      return metaInfDir;
    }

    final Ref<VirtualFile> sourceDir = Ref.create(null);
    final Ref<VirtualFile> sourceFile = Ref.create(null);
    ArtifactUtil.processElementsWithSubstitutions(root.getChildren(), context, artifactType, PackagingElementPath.EMPTY, new PackagingElementProcessor<PackagingElement<?>>() {
      @Override
      public boolean process(@NotNull PackagingElement<?> element, @NotNull PackagingElementPath path) {
        if (element instanceof FileCopyPackagingElement) {
          final VirtualFile file = ((FileCopyPackagingElement)element).findFile();
          if (file != null) {
            sourceFile.set(file);
          }
        }
        else if (element instanceof DirectoryCopyPackagingElement) {
          final VirtualFile file = ((DirectoryCopyPackagingElement)element).findFile();
          if (file != null) {
            sourceDir.set(file);
            return false;
          }
        }
        return true;
      }
    });

    if (!sourceDir.isNull()) {
      return sourceDir.get();
    }


    final Project project = context.getProject();
    return suggestBaseDir(project, sourceFile.get());
  }

  @Nullable
  private static VirtualFile suggestBaseDir(Project project, final @Nullable VirtualFile file) {
    final VirtualFile[] contentRoots = ProjectRootManager.getInstance(project).getContentRoots();
    if (file == null && contentRoots.length > 0) {
      return contentRoots[0];
    }

    if (file != null) {
      for (VirtualFile contentRoot : contentRoots) {
        if (VfsUtil.isAncestor(contentRoot, file, false)) {
          return contentRoot;
        }
      }
    }

    return project.getBaseDir();
  }

  public static Manifest readManifest(@NotNull VirtualFile manifestFile) {
    try {
      final InputStream inputStream = manifestFile.getInputStream();
      final Manifest manifest;
      try {
        manifest = new Manifest(inputStream);
      }
      finally {
        inputStream.close();
      }
      return manifest;
    }
    catch (IOException ignored) {
      return new Manifest();
    }
  }

  public static void updateManifest(VirtualFile file, final String mainClass, final List<String> classpath, final boolean replaceValues) {
    final Manifest manifest = readManifest(file);
    final Attributes mainAttributes = manifest.getMainAttributes();

    if (mainClass != null) {
      mainAttributes.put(Attributes.Name.MAIN_CLASS, mainClass);
    }
    else if (replaceValues) {
      mainAttributes.remove(Attributes.Name.MAIN_CLASS);
    }

    if (classpath != null && !classpath.isEmpty()) {
      List<String> updatedClasspath;
      if (replaceValues) {
        updatedClasspath = classpath;
      }
      else {
        updatedClasspath = new ArrayList<String>();
        final String oldClasspath = (String)mainAttributes.get(Attributes.Name.CLASS_PATH);
        if (!StringUtil.isEmpty(oldClasspath)) {
          updatedClasspath.addAll(StringUtil.split(oldClasspath, " "));
        }
        for (String path : classpath) {
          if (!updatedClasspath.contains(path)) {
            updatedClasspath.add(path);
          }
        }
      }
      mainAttributes.put(Attributes.Name.CLASS_PATH, StringUtil.join(updatedClasspath, " "));
    }
    else if (replaceValues) {
      mainAttributes.remove(Attributes.Name.CLASS_PATH);
    }

    ManifestBuilder.setVersionAttribute(mainAttributes);

    try {
      final OutputStream outputStream = file.getOutputStream(ManifestFileUtil.class);
      try {
        manifest.write(outputStream);
      }
      finally {
        outputStream.close();
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  @NotNull
  public static ManifestFileConfiguration createManifestFileConfiguration(@NotNull VirtualFile manifestFile) {
    final String path = manifestFile.getPath();
    Manifest manifest = readManifest(manifestFile);
    String mainClass = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
    final String classpathText = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
    final List<String> classpath = new ArrayList<String>();
    if (classpathText != null) {
      classpath.addAll(StringUtil.split(classpathText, " "));
    }
    return new ManifestFileConfiguration(path, classpath, mainClass);
  }

  public static List<String> getClasspathForElements(List<? extends PackagingElement<?>> elements, PackagingElementResolvingContext context, final ArtifactType artifactType) {
    final List<String> classpath = new ArrayList<String>();
    final PackagingElementProcessor<PackagingElement<?>> processor = new PackagingElementProcessor<PackagingElement<?>>() {
      @Override
      public boolean process(@NotNull PackagingElement<?> element, @NotNull PackagingElementPath path) {
        if (element instanceof FileCopyPackagingElement) {
          final String fileName = ((FileCopyPackagingElement)element).getOutputFileName();
          classpath.add(DeploymentUtil.appendToPath(path.getPathString(), fileName));
        }
        else if (element instanceof DirectoryCopyPackagingElement) {
          classpath.add(path.getPathString());
        }
        else if (element instanceof ArchivePackagingElement) {
          final String archiveName = ((ArchivePackagingElement)element).getName();
          classpath.add(DeploymentUtil.appendToPath(path.getPathString(), archiveName));
        }
        return true;
      }
    };
    for (PackagingElement<?> element : elements) {
      ArtifactUtil.processPackagingElements(element, null, processor, context, true, artifactType);
    }
    return classpath;
  }

  @Nullable
  public static VirtualFile showDialogAndCreateManifest(final ArtifactEditorContext context, final CompositePackagingElement<?> element) {
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle("Select Directory for META-INF/MANIFEST.MF file");
    final VirtualFile directory = suggestManifestFileDirectory(element, context, context.getArtifactType());
    final VirtualFile[] files = FileChooser.chooseFiles(context.getProject(), descriptor, directory);
    if (files.length != 1) {
      return null;
    }

    final Ref<IOException> exc = Ref.create(null);
    final VirtualFile file = new WriteAction<VirtualFile>() {
      protected void run(final Result<VirtualFile> result) {
        VirtualFile dir = files[0];
        try {
          if (!dir.getName().equals(MANIFEST_DIR_NAME)) {
            dir = VfsUtil.createDirectoryIfMissing(dir, MANIFEST_DIR_NAME);
          }
          final VirtualFile file = dir.createChildData(this, MANIFEST_FILE_NAME);
          final OutputStream output = file.getOutputStream(this);
          try {
            final Manifest manifest = new Manifest();
            ManifestBuilder.setVersionAttribute(manifest.getMainAttributes());
            manifest.write(output);
          }
          finally {
            output.close();
          }
          result.setResult(file);
        }
        catch (IOException e) {
          exc.set(e);
        }
      }
    }.execute().getResultObject();

    final IOException exception = exc.get();
    if (exception != null) {
      LOG.info(exception);
      Messages.showErrorDialog(context.getProject(), exception.getMessage(), CommonBundle.getErrorTitle());
      return null;
    }
    return file;
  }

  public static void addManifestFileToLayout(final @NotNull String path, final @NotNull ArtifactEditorContext context,
                                             final @NotNull CompositePackagingElement<?> element) {
    context.editLayout(context.getArtifact(), new Runnable() {
      public void run() {
        final VirtualFile file = findManifestFile(element, context, context.getArtifactType());
        if (file == null || !FileUtil.pathsEqual(file.getPath(), path)) {
          PackagingElementFactory.getInstance().addFileCopy(element, MANIFEST_DIR_NAME, path, MANIFEST_FILE_NAME);
        }
      }
    });
  }
}
