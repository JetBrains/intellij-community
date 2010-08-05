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
package com.intellij.packaging.impl.artifacts;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.elements.*;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class ArtifactUtil {
  private ArtifactUtil() {
  }

  public static CompositePackagingElement<?> copyFromRoot(@NotNull CompositePackagingElement<?> oldRoot, @NotNull Project project) {
    final CompositePackagingElement<?> newRoot = (CompositePackagingElement<?>)copyElement(oldRoot, project);
    copyChildren(oldRoot, newRoot, project);
    return newRoot;
  }


  public static void copyChildren(CompositePackagingElement<?> oldParent, CompositePackagingElement<?> newParent, @NotNull Project project) {
    for (PackagingElement<?> child : oldParent.getChildren()) {
      newParent.addOrFindChild(copyWithChildren(child, project));
    }
  }

  @NotNull
  public static <S> PackagingElement<S> copyWithChildren(@NotNull PackagingElement<S> element, @NotNull Project project) {
    final PackagingElement<S> copy = copyElement(element, project);
    if (element instanceof CompositePackagingElement<?>) {
      copyChildren((CompositePackagingElement<?>)element, (CompositePackagingElement<?>)copy, project);
    }
    return copy;
  }

  @NotNull
  private static <S> PackagingElement<S> copyElement(@NotNull PackagingElement<S> element, @NotNull Project project) {
    //noinspection unchecked
    final PackagingElement<S> copy = (PackagingElement<S>)element.getType().createEmpty(project);
    copy.loadState(element.getState());
    return copy;
  }

  public static <E extends PackagingElement<?>> boolean processPackagingElements(@NotNull Artifact artifact, @Nullable PackagingElementType<E> type,
                                                                                 @NotNull final Processor<? super E> processor,
                                                                                 final @NotNull PackagingElementResolvingContext resolvingContext,
                                                                                 final boolean processSubstitutions) {
    return processPackagingElements(artifact, type, new PackagingElementProcessor<E>() {
      @Override
      public boolean process(@NotNull E e, @NotNull PackagingElementPath path) {
        return processor.process(e);
      }
    }, resolvingContext, processSubstitutions);
  }

  public static <E extends PackagingElement<?>> boolean processPackagingElements(@NotNull Artifact artifact, @Nullable PackagingElementType<E> type,
                                                                                 @NotNull PackagingElementProcessor<? super E> processor,
                                                                                 final @NotNull PackagingElementResolvingContext resolvingContext,
                                                                                 final boolean processSubstitutions) {
    return processPackagingElements(artifact.getRootElement(), type, processor, resolvingContext, processSubstitutions, artifact.getArtifactType());
  }

  public static <E extends PackagingElement<?>> boolean processPackagingElements(final PackagingElement<?> rootElement, @Nullable PackagingElementType<E> type,
                                                                                 @NotNull PackagingElementProcessor<? super E> processor,
                                                                                 final @NotNull PackagingElementResolvingContext resolvingContext,
                                                                                 final boolean processSubstitutions,
                                                                                 final ArtifactType artifactType) {
    return processElementRecursively(rootElement, type, processor, resolvingContext, processSubstitutions, artifactType,
                          PackagingElementPath.EMPTY, new HashSet<PackagingElement<?>>());
  }

  private static <E extends PackagingElement<?>> boolean processElementsRecursively(final List<? extends PackagingElement<?>> elements,
                                                                         @Nullable PackagingElementType<E> type,
                                                                         @NotNull PackagingElementProcessor<? super E> processor,
                                                                         final @NotNull PackagingElementResolvingContext resolvingContext,
                                                                         final boolean processSubstitutions, ArtifactType artifactType,
                                                                         @NotNull PackagingElementPath path,
                                                                         Set<PackagingElement<?>> processed) {
    for (PackagingElement<?> element : elements) {
      if (!processElementRecursively(element, type, processor, resolvingContext, processSubstitutions, artifactType, path, processed)) {
        return false;
      }
    }
    return true;
  }

  private static <E extends PackagingElement<?>> boolean processElementRecursively(@NotNull PackagingElement<?> element, @Nullable PackagingElementType<E> type,
                                                                         @NotNull PackagingElementProcessor<? super E> processor,
                                                                         @NotNull PackagingElementResolvingContext resolvingContext,
                                                                         final boolean processSubstitutions,
                                                                         ArtifactType artifactType,
                                                                         @NotNull PackagingElementPath path, Set<PackagingElement<?>> processed) {
    if (!processor.shouldProcess(element) || !processed.add(element)) {
      return true;
    }
    if (type == null || element.getType().equals(type)) {
      if (!processor.process((E)element, path)) {
        return false;
      }
    }
    if (element instanceof CompositePackagingElement<?>) {
      final CompositePackagingElement<?> composite = (CompositePackagingElement<?>)element;
      return processElementsRecursively(composite.getChildren(), type, processor, resolvingContext, processSubstitutions, artifactType,
                             path.appendComposite(composite), processed);
    }
    else if (element instanceof ComplexPackagingElement<?> && processSubstitutions) {
      final ComplexPackagingElement<?> complexElement = (ComplexPackagingElement<?>)element;
      if (processor.shouldProcessSubstitution(complexElement)) {
        final List<? extends PackagingElement<?>> substitution = complexElement.getSubstitution(resolvingContext, artifactType);
        if (substitution != null) {
          return processElementsRecursively(substitution, type, processor, resolvingContext, processSubstitutions, artifactType,
                                 path.appendComplex(complexElement), processed);
        }
      }
    }
    return true;
  }

  public static void removeDuplicates(@NotNull CompositePackagingElement<?> parent) {
    List<PackagingElement<?>> prevChildren = new ArrayList<PackagingElement<?>>();

    List<PackagingElement<?>> toRemove = new ArrayList<PackagingElement<?>>();
    for (PackagingElement<?> child : parent.getChildren()) {
      if (child instanceof CompositePackagingElement<?>) {
        removeDuplicates((CompositePackagingElement<?>)child);
      }
      boolean merged = false;
      for (PackagingElement<?> prevChild : prevChildren) {
        if (child.isEqualTo(prevChild)) {
          if (child instanceof CompositePackagingElement<?>) {
            for (PackagingElement<?> childElement : ((CompositePackagingElement<?>)child).getChildren()) {
              ((CompositePackagingElement<?>)prevChild).addOrFindChild(childElement);
            }
          }
          merged = true;
          break;
        }
      }
      if (merged) {
        toRemove.add(child);
      }
      else {
        prevChildren.add(child);
      }
    }

    for (PackagingElement<?> child : toRemove) {
      parent.removeChild(child);
    }
  }

  public static <S> void copyProperties(ArtifactProperties<?> from, ArtifactProperties<S> to) {
    //noinspection unchecked
    to.loadState((S)from.getState());
  }

  @Nullable
  public static String getDefaultArtifactOutputPath(@NotNull String artifactName, final @NotNull Project project) {
    final CompilerProjectExtension extension = CompilerProjectExtension.getInstance(project);
    if (extension == null) return null;
    String outputUrl = extension.getCompilerOutputUrl();
    if (outputUrl == null || outputUrl.length() == 0) {
      final VirtualFile baseDir = project.getBaseDir();
      if (baseDir == null) return null;
      outputUrl = baseDir.getUrl() + "/out";
    }
    return VfsUtil.urlToPath(outputUrl) + "/artifacts/" + FileUtil.sanitizeFileName(artifactName);
  }

  public static <E extends PackagingElement<?>> boolean processElementsWithSubstitutions(@NotNull List<? extends PackagingElement<?>> elements,
                                        @NotNull PackagingElementResolvingContext context,
                                        @NotNull ArtifactType artifactType,
                                        @NotNull PackagingElementPath parentPath,
                                        @NotNull PackagingElementProcessor<E> processor) {
    return processElementsWithSubstitutions(elements, context, artifactType, parentPath, processor, new HashSet<PackagingElement<?>>());
  }

  private static <E extends PackagingElement<?>> boolean processElementsWithSubstitutions(@NotNull List<? extends PackagingElement<?>> elements,
                                                                                         @NotNull PackagingElementResolvingContext context,
                                                                                         @NotNull ArtifactType artifactType,
                                                                                         @NotNull PackagingElementPath parentPath,
                                                                                         @NotNull PackagingElementProcessor<E> processor,
                                                                                         final Set<PackagingElement<?>> processed) {
    for (PackagingElement<?> element : elements) {
      if (!processed.add(element)) {
        continue;
      }

      if (element instanceof ComplexPackagingElement<?> && processor.shouldProcessSubstitution((ComplexPackagingElement)element)) {
        final ComplexPackagingElement<?> complexElement = (ComplexPackagingElement<?>)element;
        final List<? extends PackagingElement<?>> substitution = complexElement.getSubstitution(context, artifactType);
        if (substitution != null &&
            !processElementsWithSubstitutions(substitution, context, artifactType, parentPath.appendComplex(complexElement), processor, processed)) {
          return false;
        }
      }
      else if (!processor.process((E)element, parentPath)) {
        return false;
      }
    }
    return true;
  }

  public static List<PackagingElement<?>> findByRelativePath(@NotNull CompositePackagingElement<?> parent, @NotNull String relativePath,
                                                             @NotNull PackagingElementResolvingContext context, @NotNull ArtifactType artifactType) {
    final List<PackagingElement<?>> result = new ArrayList<PackagingElement<?>>();
    processElementsByRelativePath(parent, relativePath, context, artifactType, PackagingElementPath.EMPTY, new PackagingElementProcessor<PackagingElement<?>>() {
      @Override
      public boolean process(@NotNull PackagingElement<?> packagingElement, @NotNull PackagingElementPath path) {
        result.add(packagingElement);
        return true;
      }
    });
    return result;
  }

  public static boolean processElementsByRelativePath(@NotNull final CompositePackagingElement<?> parent, @NotNull String relativePath,
                                                       @NotNull final PackagingElementResolvingContext context, @NotNull final ArtifactType artifactType,
                                                       @NotNull PackagingElementPath parentPath,
                                                       @NotNull final PackagingElementProcessor<PackagingElement<?>> processor) {
    relativePath = StringUtil.trimStart(relativePath, "/");
    if (relativePath.length() == 0) {
      return true;
    }

    int i = relativePath.indexOf('/');
    final String firstName = i != -1 ? relativePath.substring(0, i) : relativePath;
    final String tail = i != -1 ? relativePath.substring(i+1) : "";

    return processElementsWithSubstitutions(parent.getChildren(), context, artifactType, parentPath.appendComposite(parent), new PackagingElementProcessor<PackagingElement<?>>() {
      @Override
      public boolean process(@NotNull PackagingElement<?> element, @NotNull PackagingElementPath path) {
        boolean process = false;
        if (element instanceof CompositePackagingElement && firstName.equals(((CompositePackagingElement<?>)element).getName())) {
          process = true;
        }
        else if (element instanceof FileCopyPackagingElement) {
          final FileCopyPackagingElement fileCopy = (FileCopyPackagingElement)element;
          if (firstName.equals(fileCopy.getOutputFileName())) {
            process = true;
          }
        }
        
        if (process) {
          if (tail.length() == 0) {
            if (!processor.process(element, path)) return false;
          }
          else if (element instanceof CompositePackagingElement<?>) {
            return processElementsByRelativePath((CompositePackagingElement)element, tail, context, artifactType, path, processor);
          }
        }
        return true;
      }
    });
  }

  public static boolean processDirectoryChildren(@NotNull CompositePackagingElement<?> parent,
                                                 @NotNull PackagingElementPath pathToParent,
                                                 @NotNull String relativePath,
                                                 @NotNull final PackagingElementResolvingContext context,
                                                 @NotNull final ArtifactType artifactType,
                                                 @NotNull final PackagingElementProcessor<PackagingElement<?>> processor) {
    return processElementsByRelativePath(parent, relativePath, context, artifactType, pathToParent, new PackagingElementProcessor<PackagingElement<?>>() {
      @Override
      public boolean process(@NotNull PackagingElement<?> element, @NotNull PackagingElementPath path) {
        if (element instanceof DirectoryPackagingElement) {
          final List<PackagingElement<?>> children = ((DirectoryPackagingElement)element).getChildren();
          if (!processElementsWithSubstitutions(children, context, artifactType, path.appendComposite((DirectoryPackagingElement)element), processor)) {
            return false;
          }
        }
        return true;
      }
    });
  }

  public static void processFileOrDirectoryCopyElements(Artifact artifact,
                                                         PackagingElementProcessor<FileOrDirectoryCopyPackagingElement<?>> processor,
                                                         PackagingElementResolvingContext context,
                                                         boolean processSubstitutions) {
    processPackagingElements(artifact, PackagingElementFactoryImpl.FILE_COPY_ELEMENT_TYPE, processor, context, processSubstitutions);
    processPackagingElements(artifact, PackagingElementFactoryImpl.DIRECTORY_COPY_ELEMENT_TYPE, processor, context, processSubstitutions);
    processPackagingElements(artifact, PackagingElementFactoryImpl.EXTRACTED_DIRECTORY_ELEMENT_TYPE, processor, context, processSubstitutions);
  }

  public static Collection<Trinity<Artifact, PackagingElementPath, String>> findContainingArtifactsWithOutputPaths(@NotNull final VirtualFile file, @NotNull Project project) {
    final boolean isResourceFile = CompilerConfiguration.getInstance(project).isResourceFile(file);
    final List<Trinity<Artifact, PackagingElementPath, String>> artifacts = new ArrayList<Trinity<Artifact, PackagingElementPath, String>>();
    final PackagingElementResolvingContext context = ArtifactManager.getInstance(project).getResolvingContext();
    for (final Artifact artifact : ArtifactManager.getInstance(project).getArtifacts()) {
      processPackagingElements(artifact, null, new PackagingElementProcessor<PackagingElement<?>>() {
        @Override
        public boolean process(@NotNull PackagingElement<?> element, @NotNull PackagingElementPath path) {
          if (element instanceof FileOrDirectoryCopyPackagingElement<?>) {
            final VirtualFile root = ((FileOrDirectoryCopyPackagingElement)element).findFile();
            if (root != null && VfsUtil.isAncestor(root, file, false)) {
              final String relativePath;
              if (root.equals(file) && element instanceof FileCopyPackagingElement) {
                relativePath = ((FileCopyPackagingElement)element).getOutputFileName();
              }
              else {
                relativePath = VfsUtil.getRelativePath(file, root, '/');
              }
              artifacts.add(Trinity.create(artifact, path, relativePath));
              return false;
            }
          }
          else if (isResourceFile && element instanceof ModuleOutputPackagingElement) {
            final String relativePath = getRelativePathInSources(file, (ModuleOutputPackagingElement)element, context);
            if (relativePath != null) {
              artifacts.add(Trinity.create(artifact, path, relativePath));
              return false;
            }
          }
          return true;
        }
      }, context, true);
    }
    return artifacts;
  }

  @Nullable
  private static String getRelativePathInSources(@NotNull VirtualFile file, final @NotNull ModuleOutputPackagingElement moduleElement,
                                                @NotNull PackagingElementResolvingContext context) {
    final Module module = moduleElement.findModule(context);
    if (module != null) {
      final ModuleRootModel rootModel = context.getModulesProvider().getRootModel(module);
      for (VirtualFile sourceRoot : rootModel.getSourceRoots(false)) {
        if (VfsUtil.isAncestor(sourceRoot, file, true)) {
          return VfsUtil.getRelativePath(file, sourceRoot, '/');
        }
      }
    }
    return null;
  }

  @Nullable
  public static VirtualFile findSourceFileByOutputPath(Artifact artifact, String outputPath, PackagingElementResolvingContext context) {
    final List<VirtualFile> files = findSourceFilesByOutputPath(artifact.getRootElement(), outputPath, context, artifact.getArtifactType());
    return files.isEmpty() ? null : files.get(0);
  }

  @Nullable
  public static VirtualFile findSourceFileByOutputPath(CompositePackagingElement<?> parent, String outputPath,
                                                 PackagingElementResolvingContext context, ArtifactType artifactType) {
    final List<VirtualFile> files = findSourceFilesByOutputPath(parent, outputPath, context, artifactType);
    return files.isEmpty() ? null : files.get(0);
  }

  public static List<VirtualFile> findSourceFilesByOutputPath(CompositePackagingElement<?> parent, final String outputPath,
                                                              final PackagingElementResolvingContext context, final ArtifactType artifactType) {
    final String path = StringUtil.trimStart(outputPath, "/");
    if (path.length() == 0) {
      return Collections.emptyList();
    }

    int i = path.indexOf('/');
    final String firstName = i != -1 ? path.substring(0, i) : path;
    final String tail = i != -1 ? path.substring(i+1) : "";

    final List<VirtualFile> result = new SmartList<VirtualFile>();
    processElementsWithSubstitutions(parent.getChildren(), context, artifactType, PackagingElementPath.EMPTY, new PackagingElementProcessor<PackagingElement<?>>() {
      @Override
      public boolean process(@NotNull PackagingElement<?> element, @NotNull PackagingElementPath elementPath) {
        //todo[nik] replace by method findSourceFile() in PackagingElement
        if (element instanceof CompositePackagingElement) {
          final CompositePackagingElement<?> compositeElement = (CompositePackagingElement<?>)element;
          if (firstName.equals(compositeElement.getName())) {
            result.addAll(findSourceFilesByOutputPath(compositeElement, tail, context, artifactType));
          }
        }
        else if (element instanceof FileCopyPackagingElement) {
          final FileCopyPackagingElement fileCopyElement = (FileCopyPackagingElement)element;
          if (firstName.equals(fileCopyElement.getOutputFileName()) && tail.length() == 0) {
            ContainerUtil.addIfNotNull(fileCopyElement.findFile(), result);
          }
        }
        else if (element instanceof DirectoryCopyPackagingElement || element instanceof ExtractedDirectoryPackagingElement) {
          final VirtualFile sourceRoot = ((FileOrDirectoryCopyPackagingElement<?>)element).findFile();
          if (sourceRoot != null) {
            ContainerUtil.addIfNotNull(sourceRoot.findFileByRelativePath(path), result);
          }
        }
        else if (element instanceof ModuleOutputPackagingElement) {
          final Module module = ((ModuleOutputPackagingElement)element).findModule(context);
          if (module != null) {
            final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(context.getProject());
            final ModuleRootModel rootModel = context.getModulesProvider().getRootModel(module);
            for (VirtualFile sourceRoot : rootModel.getSourceRoots(false)) {
              final VirtualFile sourceFile = sourceRoot.findFileByRelativePath(path);
              if (sourceFile != null && compilerConfiguration.isResourceFile(sourceFile)) {
                result.add(sourceFile);
              }
            }
          }
        }
        return true;
      }
    });

    return result;
  }

  public static boolean processParents(@NotNull Artifact artifact,
                                       @NotNull PackagingElementResolvingContext context,
                                       @NotNull ParentElementProcessor processor,
                                       int maxLevel) {
    return processParents(artifact, context, processor, FList.<Pair<Artifact, CompositePackagingElement<?>>>emptyList(), maxLevel,
                          new HashSet<Artifact>());
  }

  private static boolean processParents(@NotNull final Artifact artifact, @NotNull final PackagingElementResolvingContext context,
                                        @NotNull final ParentElementProcessor processor, FList<Pair<Artifact, CompositePackagingElement<?>>> pathToElement,
                                        final int maxLevel, final Set<Artifact> processed) {
    if (!processed.add(artifact)) return true;

    final FList<Pair<Artifact, CompositePackagingElement<?>>> pathFromRoot;
    final CompositePackagingElement<?> rootElement = artifact.getRootElement();
    if (rootElement instanceof ArtifactRootElement<?>) {
      pathFromRoot = pathToElement;
    }
    else {
      if (!processor.process(rootElement, pathToElement, artifact)) {
        return false;
      }
      pathFromRoot = pathToElement.prepend(new Pair<Artifact, CompositePackagingElement<?>>(artifact, rootElement));
    }
    if (pathFromRoot.size() > maxLevel) return true;

    for (final Artifact anArtifact : context.getArtifactModel().getArtifacts()) {
      if (processed.contains(anArtifact)) continue;

      final PackagingElementProcessor<ArtifactPackagingElement> elementProcessor =
          new PackagingElementProcessor<ArtifactPackagingElement>() {
            @Override
            public boolean shouldProcessSubstitution(ComplexPackagingElement<?> element) {
              return !(element instanceof ArtifactPackagingElement);
            }

            @Override
            public boolean process(@NotNull ArtifactPackagingElement element, @NotNull PackagingElementPath path) {
              if (artifact.getName().equals(element.getArtifactName())) {
                FList<Pair<Artifact, CompositePackagingElement<?>>> currentPath = pathFromRoot;
                final List<CompositePackagingElement<?>> parents = path.getParents();
                for (int i = 0, parentsSize = parents.size(); i < parentsSize - 1; i++) {
                  CompositePackagingElement<?> parent = parents.get(i);
                  if (!processor.process(parent, currentPath, anArtifact)) {
                    return false;
                  }
                  currentPath = currentPath.prepend(new Pair<Artifact, CompositePackagingElement<?>>(anArtifact, parent));
                  if (currentPath.size() > maxLevel) {
                    return true;
                  }
                }

                if (!parents.isEmpty()) {
                  CompositePackagingElement<?> lastParent = parents.get(parents.size() - 1);
                  if (lastParent instanceof ArtifactRootElement<?> && !processor.process(lastParent, currentPath, anArtifact)) {
                    return false;
                  }
                }
                return processParents(anArtifact, context, processor, currentPath, maxLevel, processed);
              }
              return true;
            }
          };
      if (!processPackagingElements(anArtifact, ArtifactElementType.ARTIFACT_ELEMENT_TYPE, elementProcessor, context, true)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isArchiveName(String name) {
    return name.length() >= 4 && name.charAt(name.length() - 4) == '.' && StringUtil.endsWithIgnoreCase(name, "ar");
  }

  public static void removeChildrenRecursively(@NotNull CompositePackagingElement<?> element, @NotNull Condition<PackagingElement<?>> condition) {
    List<PackagingElement<?>> toRemove = new ArrayList<PackagingElement<?>>();
    for (PackagingElement<?> child : element.getChildren()) {
      if (child instanceof CompositePackagingElement<?>) {
        final CompositePackagingElement<?> compositeChild = (CompositePackagingElement<?>)child;
        removeChildrenRecursively(compositeChild, condition);
        if (compositeChild.getChildren().isEmpty()) {
          toRemove.add(child);
        }
      }
      else if (condition.value(child)) {
        toRemove.add(child);
      }
    }

    element.removeChildren(toRemove);
  }

  public static boolean shouldClearArtifactOutputBeforeRebuild(Artifact artifact) {
    final String outputPath = artifact.getOutputPath();
    return !StringUtil.isEmpty(outputPath) && artifact.getRootElement() instanceof ArtifactRootElement<?>;
  }
}

