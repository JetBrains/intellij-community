// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.spi.psi.SPIFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.util.ClassSet;

import java.util.HashMap;
import java.util.Map;

import static org.jetbrains.uast.util.ClassSetKt.*;

/**
 * This ModificationTracker is incremented if changes in file (class) of VFS could change the number of stereotype components scanned
 * by "component scan" models (@ComponentScan/<ctx:component-scan ../>/ repositories scan, etc.)
 * <p/>
 * VirtualFileListener: count++ if a file was added/moved/deleted.
 * This file could be scanned by component scan (if it's a stereotype)
 * PsiTreeChangeListener: count++ on: adding/removing/editing annotations and adding/removing of inner classes.
 * As if it is spring stereotype anno (@Component, @Configuration), this class could become part of the scanned model.
 */
@ApiStatus.Internal
public class OuterModelsModificationTracker extends SimpleModificationTracker {

  private final boolean myTrackConfigurationFiles;

  public OuterModelsModificationTracker(Project project, Disposable parent, boolean useUastBased, boolean trackConfigurationFiles) {
    myTrackConfigurationFiles = trackConfigurationFiles;
    PsiManager.getInstance(project).addPsiTreeChangeListener(
      useUastBased ? new MyUastPsiTreeChangeAdapter(project) : new MyJavaPsiTreeChangeAdapter(),
      parent
    );

    final MessageBusConnection connection = project.getMessageBus().connect(parent);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new MyVirtualFileListener(project)));
  }

  private boolean processConfigFileChange(PsiFile psiFile) {
    String languageId = psiFile == null ? null : psiFile.getLanguage().getID();
    if (myTrackConfigurationFiles && "Properties".equals(languageId)) {
      incModificationCount();
      return true;
    }

    if (psiFile instanceof SPIFile) {
      incModificationCount();
      return true;
    }

    if (myTrackConfigurationFiles && "yaml".equals(languageId)) {
      incModificationCount();
      return true;
    }

    return false;
  }

  private final class MyVirtualFileListener implements VirtualFileListener {

    private final ProjectFileIndex myFileIndex;

    private MyVirtualFileListener(Project project) {
      myFileIndex = ProjectFileIndex.getInstance(project);
    }

    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
      incModificationCountIfMine(event);
    }

    @Override
    public void beforeFileDeletion(@NotNull VirtualFileEvent event) {
      incModificationCountIfMine(event); // before... otherwise a project is null
    }

    @Override
    public void fileMoved(final @NotNull VirtualFileMoveEvent event) {
      incModificationCountIfMine(event);
    }

    @Override
    public void propertyChanged(final @NotNull VirtualFilePropertyEvent event) {
      if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
        incModificationCountIfMine(event);
      }
    }

    private void incModificationCountIfMine(@NotNull VirtualFileEvent event) {
      final VirtualFile file = event.getFile();

      if (!myFileIndex.isInContent(file)) {
        return;
      }

      if (!file.isDirectory() &&
          isIgnoredFileType(file.getFileType())) {
        return;
      }

      incModificationCount();
    }

    @SuppressWarnings({"removal", "deprecation"})
    private static boolean isIgnoredFileType(@NotNull FileType type) {
      return type.equals(HtmlFileType.INSTANCE) ||
             type instanceof LanguageFileType && "JavaScript".equals(((LanguageFileType)type).getLanguage().getID()) ||
             type.equals(StdFileTypes.JSP) ||
             type.equals(StdFileTypes.JSPX);
    }
  }

  private class MyJavaPsiTreeChangeAdapter extends PsiTreeChangeAdapter {

    @Override
    public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
      processChange(event, event.getParent(), event.getChild());
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      if (event instanceof PsiTreeChangeEventImpl &&
          ((PsiTreeChangeEventImpl)event).isGenericChange()) {
        return;
      }
      processChange(event, event.getParent(), event.getChild());
    }

    @Override
    public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
      processChange(event, event.getParent(), event.getChild());
    }

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
      processChange(event, event.getParent(), event.getChild());
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      processChange(event, event.getParent(), event.getChild());
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
      processChange(event, event.getParent(), event.getOldChild());
    }

    private void processChange(final PsiTreeChangeEvent event, PsiElement parent, PsiElement child) {
      PsiFile psiFile = event.getFile();
      if (processConfigFileChange(psiFile)) return;

      if (!(psiFile instanceof PsiClassOwner)) {
        return;
      }

      // added/removed annotation
      if (parent instanceof PsiModifierList && child instanceof PsiAnnotation) {
        checkRelevantAnnotation((PsiAnnotation)child);
        return;
      }
      // psiClass modifier changed (static, public)
      if (parent instanceof PsiModifierList && parent.getParent() instanceof PsiClass) {
        incModificationCount();
        return;
      }
      // added/removed inner class (it could contain stereotype anno)
      if (parent instanceof PsiClass) {
        if (child instanceof PsiClass || event.getNewChild() instanceof PsiClass || event.getOldChild() instanceof PsiClass) {
          incModificationCount();
        }
        return;
      }

      if (parent instanceof PsiImportList || // add import
          child instanceof PsiImportList || // remove import
          PsiTreeUtil.getParentOfType(parent, PsiImportList.class) != null) // change import identifier (completion, typing)
      {
        incModificationCount();
        return;
      }

      // editing on existing or inside annotation
      final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(parent, PsiAnnotation.class);
      if (annotation != null) {
        checkRelevantAnnotation(annotation);
      }
    }

    private void checkRelevantAnnotation(@NotNull PsiAnnotation annotation) {
      final PsiModifierListOwner modifierListOwner = PsiTreeUtil.getParentOfType(annotation, PsiModifierListOwner.class);

      if (modifierListOwner == null || // just added annotation
          modifierListOwner instanceof PsiClass ||
          modifierListOwner instanceof PsiMethod) {
        incModificationCount();
      }
    }
  }

  private class MyUastPsiTreeChangeAdapter extends PsiTreeChangeAdapter {

    private final Project myProject;
    private final Map<String, CachedValue<MyPsiPossibleTypes>> myPsiPossibleTypes = new HashMap<>();

    private MyUastPsiTreeChangeAdapter(Project project) {
      myProject = project;
    }

    @Override
    public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
      processChange(event, event.getParent(), event.getChild());
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      if (event instanceof PsiTreeChangeEventImpl &&
          ((PsiTreeChangeEventImpl)event).isGenericChange()) {
        return;
      }
      processChange(event, event.getParent(), event.getChild());
    }

    @Override
    public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
      processChange(event, event.getParent(), event.getChild());
    }

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
      processChange(event, event.getParent(), event.getChild());
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      processChange(event, event.getParent(), event.getChild());
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
      processChange(event, event.getParent(), event.getOldChild());
    }

    private void processChange(final PsiTreeChangeEvent event, PsiElement parent, PsiElement child) {
      PsiFile psiFile = event.getFile();
      if (processConfigFileChange(psiFile)) return;

      if (!(psiFile instanceof PsiClassOwner)) {
        return;
      }

      // do not load file content on file creation (`MyVirtualFileListener` will signal itself)
      // see `ListenerListTest.test all methods resolved`
      if (parent instanceof PsiFile && child == null) {
        return;
      }

      final var possiblePsiTypes = getPossiblePsiTypesFor(psiFile.getLanguage().getID());
      if (possiblePsiTypes == null) {
        return;
      }

      final var newChild = event.getNewChild();
      final var grandParent = parent == null ? null : parent.getParent();
      final var firstSibling = parent != null && parent.isValid() ? parent.getFirstChild() : null;
      PsiElement unsafeGrandChild = getUnsafeGrandChild(child);

      if (isRelevantAnnotation(child, possiblePsiTypes)                                              // removed annotation
          || isRelevantAnnotation(unsafeGrandChild, possiblePsiTypes)                                // removed annotation
          || isRelevantAnnotation(newChild, possiblePsiTypes)                                        // added annotation
          || (isInstanceOf(grandParent, possiblePsiTypes.forClasses)                                 // modifier changed (static, public)
              && !isInstanceOf(parent, possiblePsiTypes.forAnnotationOwners))
          || ((isInstanceOf(parent, possiblePsiTypes.forClasses)                                     // added/removed inner class
               || isInstanceOf(grandParent, possiblePsiTypes.forClasses))
              && (isInstanceOf(child, possiblePsiTypes.forClasses)
                  || isInstanceOf(event.getNewChild(), possiblePsiTypes.forClasses)
                  || isInstanceOf(event.getOldChild(), possiblePsiTypes.forClasses)))
          || isInstanceOf(firstSibling, possiblePsiTypes.forImports)                                 // added import
          || isInstanceOf(unsafeGrandChild, possiblePsiTypes.forImports)                             // removed import
          || isInstanceOf(child, possiblePsiTypes.forImports)                                        // removed import
          || null != PsiTreeUtil.findFirstParent(parent,
                                                 it -> isInstanceOf(it, possiblePsiTypes.forImports) // changed import (completion, typing)
                                                       || isRelevantAnnotation(it, possiblePsiTypes))// editing on or inside annotation
          || child instanceof LazyParseablePsiElement
      ) {
        incModificationCount();
      }
    }

    private static @Nullable PsiElement getUnsafeGrandChild(PsiElement child) {
      PsiElement unsafeGrandChild = null;
      if (!(child instanceof LazyParseablePsiElement)) { // Chameleons unconditionally log an error IDEA-255174
        try {
          unsafeGrandChild = child == null ? null : child.getFirstChild();
        }
        catch (PsiInvalidElementAccessException ignored) {
          // We cannot check if the child is valid since most of the time
          // they are indeed invalid due to user typing, so the `getFirstChild()`
          // may throw an exception.
          // But we are forced here to ask for the child
          // since we do not have any other information.
        }
      }
      return unsafeGrandChild;
    }

    private static boolean isRelevantAnnotation(@Nullable PsiElement psiElement, @NotNull MyPsiPossibleTypes possiblePsiTypes) {
      if (!isInstanceOf(psiElement, possiblePsiTypes.forAnnotations)) {
        return false;
      }

      final var modifierListOwner = PsiTreeUtil.findFirstParent(
        psiElement, it -> isInstanceOf(it, possiblePsiTypes.forAnnotationOwners) && !isInstanceOf(it, possiblePsiTypes.forAnnotations));

      return modifierListOwner == null // just added annotation
             || isInstanceOf(modifierListOwner, possiblePsiTypes.forClasses)
             || (isInstanceOf(modifierListOwner, possiblePsiTypes.forMethods)
                 && !isInstanceOf(modifierListOwner, possiblePsiTypes.forVariables))
             || isInstanceOf(modifierListOwner, possiblePsiTypes.forPackageStatements);
    }

    private @Nullable MyPsiPossibleTypes getPossiblePsiTypesFor(@NotNull String languageId) {
      return myPsiPossibleTypes.computeIfAbsent(languageId, (_key) ->
        CachedValuesManager.getManager(myProject).createCachedValue(() -> {
          final var uastLanguagePlugin =
            ContainerUtil.find(UastLanguagePlugin.Companion.getInstances(), it -> languageId.equals(it.getLanguage().getID()));
          // drops cache on plugin dynamic reload
          return CachedValueProvider.Result
            .create(uastLanguagePlugin == null ? null : new MyPsiPossibleTypes(uastLanguagePlugin), NEVER_CHANGED);
        })
      ).getValue();
    }
  }

  // It is specially kept as simple as possible for performance and readability
  private static class MyPsiPossibleTypes {
    public final @NotNull ClassSet<PsiElement> forClasses;
    public final @NotNull ClassSet<PsiElement> forMethods;
    public final @NotNull ClassSet<PsiElement> forVariables;
    public final @NotNull ClassSet<PsiElement> forImports;
    public final @NotNull ClassSet<PsiElement> forAnnotations;
    public final @NotNull ClassSet<PsiElement> forAnnotationOwners;
    public final @NotNull ClassSet<PsiElement> forPackageStatements;

    private MyPsiPossibleTypes(@NotNull UastLanguagePlugin uastPlugin) {
      this.forClasses = uastPlugin.getPossiblePsiSourceTypes(UClass.class);
      this.forMethods = uastPlugin.getPossiblePsiSourceTypes(UMethod.class);
      this.forVariables = uastPlugin.getPossiblePsiSourceTypes(UVariable.class);
      this.forImports = uastPlugin.getPossiblePsiSourceTypes(UImportStatement.class);
      this.forAnnotations = uastPlugin.getPossiblePsiSourceTypes(UAnnotation.class);
      this.forPackageStatements = classSetOf(PsiPackageStatement.class);
      this.forAnnotationOwners = classSetsUnion(uastPlugin.getPossiblePsiSourceTypes(UAnnotated.class), forPackageStatements);
    }
  }
}
