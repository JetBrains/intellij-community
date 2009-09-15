package com.intellij.packaging.impl.elements;

import com.intellij.openapi.compiler.make.ManifestBuilder;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.PackagingElementProcessor;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import com.intellij.util.PathUtil;
import com.intellij.util.Processor;
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

  @Nullable
  public static VirtualFile findManifestFile(@NotNull CompositePackagingElement<?> root, PackagingElementResolvingContext context, ArtifactType artifactType) {
    return ArtifactUtil.findSourceFileByOutputPath(root, MANIFEST_PATH, context, artifactType);
  }

  @NotNull 
  public static String suggestManifestFilePathAndAddElement(@NotNull CompositePackagingElement<?> root, PackagingElementResolvingContext context, ArtifactType artifactType) {
    final VirtualFile metaInfDir = ArtifactUtil.findSourceFileByOutputPath(root, MANIFEST_DIR_NAME, context, artifactType);
    if (metaInfDir != null) {
      return metaInfDir.getPath() + "/" + MANIFEST_FILE_NAME;
    }

    final Ref<VirtualFile> sourceDir = Ref.create(null);
    final Ref<VirtualFile> sourceFile = Ref.create(null);
    ArtifactUtil.processElements(root.getChildren(), context, artifactType, new Processor<PackagingElement<?>>() {
      public boolean process(PackagingElement<?> element) {
        if (element instanceof FileCopyPackagingElement) {
          final VirtualFile file = ((FileCopyPackagingElement)element).findFile();
          if (file != null) {
            if (file.isDirectory()) {
              sourceDir.set(file);
              return false;
            }
            sourceFile.set(file);
          }
        }
        return true;
      }
    });

    if (!sourceDir.isNull()) {
      return sourceDir.get().getPath() + "/" + MANIFEST_PATH;
    }


    final Project project = context.getProject();
    final VirtualFile dir = suggestBaseDir(project, sourceFile.get());
    String filePath = dir.getPath() + "/" + MANIFEST_PATH;
    PackagingElementFactory.getInstance().addFileCopy(root, MANIFEST_DIR_NAME, filePath);
    return filePath;
  }

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

  public static void updateManifest(VirtualFile file, ManifestFileConfiguration configuration) {
    final Manifest manifest = readManifest(file);
    final Attributes mainAttributes = manifest.getMainAttributes();

    final String mainClass = configuration.getMainClass();
    if (mainClass != null) {
      mainAttributes.put(Attributes.Name.MAIN_CLASS, mainClass);
    }
    else {
      mainAttributes.remove(Attributes.Name.MAIN_CLASS);
    }

    final List<String> classpath = configuration.getClasspath();
    if (classpath != null && !classpath.isEmpty()) {
      mainAttributes.put(Attributes.Name.CLASS_PATH, StringUtil.join(classpath, " "));
    }
    else {
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
  public static ManifestFileConfiguration createManifestFileConfiguration(CompositePackagingElement<?> element,
                                                                    final PackagingElementResolvingContext context, final ArtifactType artifactType) {
    final VirtualFile manifestFile = findManifestFile(element, context, artifactType);
    final List<String> classpath = new ArrayList<String>();
    String mainClass = null;
    final String path;
    if (manifestFile != null) {
      path = manifestFile.getPath();
      Manifest manifest = readManifest(manifestFile);
      mainClass = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
      final String classpathText = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
      if (classpathText != null) {
        classpath.addAll(StringUtil.split(classpathText, " "));
      }
    }
    else {
      path = null;
    }
    return new ManifestFileConfiguration(classpath, mainClass, path);
  }

  public static List<String> getClasspathForElements(List<? extends PackagingElement<?>> elements, PackagingElementResolvingContext context, final ArtifactType artifactType) {
    final List<String> classpath = new ArrayList<String>();
    final PackagingElementProcessor<PackagingElement<?>> processor = new PackagingElementProcessor<PackagingElement<?>>() {
      @Override
      public boolean process(@NotNull List<CompositePackagingElement<?>> parents, @NotNull PackagingElement<?> element) {
        if (element instanceof FileCopyPackagingElement) {
          final String fileName = ((FileCopyPackagingElement)element).getOutputFileName();
          classpath.add(DeploymentUtil.appendToPath(getPathFromRoot(parents, "/"), fileName));
        }
        else if (element instanceof ArchivePackagingElement) {
          final String archiveName = ((ArchivePackagingElement)element).getName();
          classpath.add(DeploymentUtil.appendToPath(getPathFromRoot(parents, "/"), archiveName));
        }
        return true;
      }
    };
    for (PackagingElement<?> element : elements) {
      ArtifactUtil.processPackagingElements(element, null, processor, context, true, artifactType);
    }
    return classpath;
  }
}
