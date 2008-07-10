/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.impl;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.xml.*;
import com.intellij.util.xml.structure.DomStructureViewBuilder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class DomServiceImpl extends DomService {

  public ModelMerger createModelMerger() {
    return new ModelMergerImpl();
  }

  @NotNull
  public EvaluatedXmlName getEvaluatedXmlName(@NotNull final DomElement element) {
    return DomManagerImpl.getDomInvocationHandler(element).getXmlName();
  }

  public Collection<VirtualFile> getDomFileCandidates(Class<? extends DomElement> description, Project project) {
    return FileBasedIndex.getInstance().getContainingFiles(DomFileIndex.NAME, description.getName(), VirtualFileFilter.ALL);
  }

  public <T extends DomElement> List<DomFileElement<T>> getFileElements(final Class<T> clazz, final Project project, @Nullable final GlobalSearchScope scope) {
    final Collection<VirtualFile> list = scope == null ? getDomFileCandidates(clazz, project) : getDomFileCandidates(clazz, project, scope);
    final ArrayList<DomFileElement<T>> result = new ArrayList<DomFileElement<T>>(list.size());
    for (VirtualFile file : list) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile instanceof XmlFile) {
        final DomFileElement<T> element = DomManager.getDomManager(project).getFileElement((XmlFile)psiFile, clazz);
        if (element != null) {
          result.add(element);
        }
      }
    }

    return result;
  }


  public StructureViewBuilder createSimpleStructureViewBuilder(final XmlFile file, final Function<DomElement, StructureViewMode> modeProvider) {
    return new DomStructureViewBuilder(file, modeProvider);
  }

}
