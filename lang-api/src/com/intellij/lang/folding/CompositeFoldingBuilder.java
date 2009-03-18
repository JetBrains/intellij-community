/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Used by LanguageFolding class if more than one FoldingBuilder were specified
 * for a particular language.
 *
 * @author Konstantin Bulenkov
 * @see com.intellij.lang.folding.LanguageFolding
 * @since 9.0
 */
class CompositeFoldingBuilder implements FoldingBuilder {
  private final List<FoldingBuilder> myBuilders;
  private final Map<VirtualFile, Map<ASTNode, FoldingBuilder>> foldings = new HashMap<VirtualFile, Map<ASTNode, FoldingBuilder>>();
  private final FileEditorManagerListener myListener;
  private final List<Integer> myProjectsCache = new ArrayList<Integer>();

  CompositeFoldingBuilder(List<FoldingBuilder> builders) {
    myBuilders = builders;
    myListener = new FileEditorManagerAdapter() {
      @Override
      public void fileClosed(FileEditorManager source, VirtualFile file) {
        foldings.remove(file);
      }
    };
  }

  public FoldingDescriptor[] buildFoldRegions(ASTNode node, Document document) {
    final PsiElement psi = node.getPsi();
    if (psi == null) return FoldingDescriptor.EMPTY;
    checkSubscription(psi.getProject());
    final PsiFile file = PsiDocumentManager.getInstance(psi.getProject()).getPsiFile(document);
    final VirtualFile vf;
    if (file == null || (vf = file.getVirtualFile()) == null) return FoldingDescriptor.EMPTY;

    final List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
    final Map<ASTNode, FoldingBuilder> builders = new HashMap<ASTNode, FoldingBuilder>();
    foldings.put(vf, builders);

    for (FoldingBuilder builder : myBuilders) {
      final FoldingDescriptor[] foldingDescriptors = builder.buildFoldRegions(node, document);
      if (foldingDescriptors.length > 0) {
        descriptors.addAll(Arrays.asList(foldingDescriptors));
        for (FoldingDescriptor descriptor : foldingDescriptors) {
          builders.put(descriptor.getElement(), builder);
        }
      }
    }
    return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
  }

  private void checkSubscription(Project project) {
    if (! myProjectsCache.contains(project.hashCode())) {
      project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, myListener);
      myProjectsCache.add(project.hashCode());
    }
  }


  public String getPlaceholderText(ASTNode node) {
    final FoldingBuilder builder = findBuilderByASTNode(node);
    return builder == null ? node.getText() : builder.getPlaceholderText(node);
  }

  @Nullable
  private FoldingBuilder findBuilderByASTNode(ASTNode node) {
    final PsiElement psi = node.getPsi();
    if (psi == null) return null;

    final PsiFile file = psi.getContainingFile();
    final VirtualFile vf = file == null ? null : file.getVirtualFile();
    final Map<ASTNode, FoldingBuilder> builders;

    if (vf == null || (builders = foldings.get(vf)) == null) return null;

    return builders.get(node);
  }

  public boolean isCollapsedByDefault(ASTNode node) {
    final FoldingBuilder builder = findBuilderByASTNode(node);
    return builder == null ? false : builder.isCollapsedByDefault(node);
  }
}
