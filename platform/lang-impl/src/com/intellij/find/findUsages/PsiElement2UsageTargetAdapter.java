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

package com.intellij.find.findUsages;

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.find.FindManager;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.ui.ComputableIcon;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.PsiElementUsageTarget;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author max
 */
public class PsiElement2UsageTargetAdapter implements PsiElementUsageTarget, TypeSafeDataProvider {
  private final SmartPsiElementPointer myPointer;
  private final MyItemPresentation myPresentation;

  public PsiElement2UsageTargetAdapter(final PsiElement element) {
    myPointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);

    if (!(element instanceof NavigationItem)) {
      throw new IllegalArgumentException("Element is not a navigation item: " + element);
    }

    myPresentation = new MyItemPresentation();
  }

  public String getName() {
    return getNavigationItem().getName();
  }

  public ItemPresentation getPresentation() {
    return myPresentation;
  }

  public void navigate(boolean requestFocus) {
    if (!canNavigate()) return;
    getNavigationItem().navigate(requestFocus);
  }

  public boolean canNavigate() {
    return isValid() && getNavigationItem().canNavigate();
  }

  public boolean canNavigateToSource() {
    return isValid() && getNavigationItem().canNavigateToSource();
  }

  private NavigationItem getNavigationItem() {
    return (NavigationItem)getElement();
  }

  public FileStatus getFileStatus() {
    return isValid() ? getNavigationItem().getFileStatus() : FileStatus.NOT_CHANGED;
  }

  public String toString() {
    return myPresentation.getPresentableText();
  }

  public void findUsages() {
    PsiElement element = getElement();
    FindManager.getInstance(element.getProject()).findUsages(element);
  }

  public PsiElement getElement() {
    return myPointer.getElement();
  }

  public void findUsagesInEditor(@NotNull FileEditor editor) {
    PsiElement element = getElement();
    FindManager.getInstance(element.getProject()).findUsagesInEditor(element, editor);
  }

  public void highlightUsages(PsiFile file, Editor editor, boolean clearHighlights) {
    PsiElement target = getElement();

    if (file instanceof PsiCompiledElement) file = (PsiFile)((PsiCompiledElement)file).getMirror();

    final FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(target.getProject())).getFindUsagesManager();
    final FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(target, true);
    Collection<PsiReference> refs;

    // in case of injected file, use host file to highlight all occurrences of the target in each injected file
    PsiFile context = InjectedLanguageUtil.getTopLevelFile(file);
    SearchScope searchScope = new LocalSearchScope(context);
    if (handler != null) {
      refs = handler.findReferencesToHighlight(target, searchScope);
    }
    else {
      refs = ReferencesSearch.search(target, searchScope, false).findAll();
    }

    new HighlightUsagesHandler.DoHighlightRunnable(new ArrayList<PsiReference>(refs),
                                                   target.getProject(),
                                                   target,
                                                   editor,
                                                   context,
                                                   clearHighlights).run();
  }

  public boolean isValid() {
    return getElement() != null;
  }

  public boolean isReadOnly() {
    return isValid() && !getElement().isWritable();
  }

  public VirtualFile[] getFiles() {
    if (!isValid()) return null;

    final PsiFile psiFile = getElement().getContainingFile();
    if (psiFile == null) return null;

    final VirtualFile virtualFile = psiFile.getVirtualFile();
    return virtualFile == null ? null : new VirtualFile[]{virtualFile};
  }

  public void update() {
    myPresentation.update();
  }

  public static PsiElement2UsageTargetAdapter[] convert(PsiElement[] psiElements) {
    PsiElement2UsageTargetAdapter[] targets = new PsiElement2UsageTargetAdapter[psiElements.length];
    for (int i = 0; i < targets.length; i++) {
      targets[i] = new PsiElement2UsageTargetAdapter(psiElements[i]);
    }

    return targets;
  }

  public void calcData(final DataKey key, final DataSink sink) {
    if (key == UsageView.USAGE_INFO_KEY) {
      PsiElement element = getElement();
      if (element != null && element.getTextRange() != null) {
        sink.put(UsageView.USAGE_INFO_KEY, new UsageInfo(element));
      }
    }
  }

  private class MyItemPresentation implements ItemPresentation {
    private String myPresentableText;
    private ComputableIcon myIconOpen;
    private ComputableIcon myIconClosed;

    public MyItemPresentation() {
      update();
    }

    public void update() {
      final PsiElement element = getElement();
      if (element != null) {
        final ItemPresentation presentation = ((NavigationItem)element).getPresentation();
        myIconOpen = presentation != null ? ComputableIcon.create(presentation, true) : null;
        myIconClosed = presentation != null ? ComputableIcon.create(presentation, false) : null;
        myPresentableText = UsageViewUtil.createNodeText(element);
        if (myIconOpen == null || myIconClosed == null) {
          if (element instanceof PsiMetaOwner) {
            final PsiMetaOwner psiMetaOwner = (PsiMetaOwner)element;
            final PsiMetaData metaData = psiMetaOwner.getMetaData();
            if (metaData instanceof PsiPresentableMetaData) {
              final PsiPresentableMetaData psiPresentableMetaData = (PsiPresentableMetaData)metaData;
              if (myIconOpen == null) myIconOpen = ComputableIcon.create(psiPresentableMetaData);
              if (myIconClosed == null) myIconClosed = ComputableIcon.create(psiPresentableMetaData);
            }
          }
          else if (element instanceof PsiFile) {
            final PsiFile psiFile = (PsiFile)element;
            final VirtualFile virtualFile = psiFile.getVirtualFile();
            if (virtualFile != null) {
              myIconOpen = ComputableIcon.create(virtualFile);
              myIconClosed = ComputableIcon.create(virtualFile);
            }
          }
        }
      }
    }

    public String getPresentableText() {
      return myPresentableText;
    }

    public String getLocationString() {
      return null;
    }

    public TextAttributesKey getTextAttributesKey() {
      return null;
    }

    public Icon getIcon(boolean open) {
      return open ? myIconOpen.getIcon() : myIconClosed.getIcon();
    }
  }
}
