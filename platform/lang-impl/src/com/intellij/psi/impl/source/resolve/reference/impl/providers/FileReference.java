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

package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.reference.impl.CachingReference;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiFileSystemItemProcessor;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.rename.BindablePsiReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.FilteringProcessor;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author cdr
 */
public class FileReference implements FileReferenceOwner, PsiPolyVariantReference,
                                      QuickFixProvider<FileReference>, LocalQuickFixProvider,
                                      EmptyResolveMessageProvider, BindablePsiReference {

  public static final FileReference[] EMPTY = new FileReference[0];
  private static final TObjectHashingStrategy<ResolveResult> RESOLVE_RESULT_HASHING_STRATEGY = new TObjectHashingStrategy<ResolveResult>() {
    @Override
    public int computeHashCode(ResolveResult object) {
      PsiFileSystemItem fileSystemItem = (PsiFileSystemItem)object.getElement();
      if (fileSystemItem == null) {
        return 0;
      }
      VirtualFile file = fileSystemItem.getVirtualFile();
      return file == null ? 0 : file.hashCode();
    }

    @Override
    public boolean equals(ResolveResult o1, ResolveResult o2) {
      PsiFileSystemItem element1 = (PsiFileSystemItem)o1.getElement();
      PsiFileSystemItem element2 = (PsiFileSystemItem)o2.getElement();
      if (element1 == null || element2 == null) return Comparing.equal(element1, element2);
      return Comparing.equal(element1.getVirtualFile(), element2.getVirtualFile());
    }
  };

  private static final TObjectHashingStrategy<PsiElement> VARIANTS_HASHING_STRATEGY = new TObjectHashingStrategy<PsiElement>() {
    @Override
    public int computeHashCode(final PsiElement object) {
      if (object instanceof PsiNamedElement) {
        final String name = ((PsiNamedElement)object).getName();
        if (name != null) {
          return name.hashCode();
        }
      }
      return object.hashCode();
    }

    @Override
    public boolean equals(final PsiElement o1, final PsiElement o2) {
      if (o1 instanceof PsiNamedElement && o2 instanceof PsiNamedElement) {
        return Comparing.equal(((PsiNamedElement)o1).getName(), ((PsiNamedElement)o2).getName());
      }
      return o1.equals(o2);
    }
  };

  private final int myIndex;
  private TextRange myRange;
  private final String myText;
  @NotNull private final FileReferenceSet myFileReferenceSet;

  public FileReference(@NotNull final FileReferenceSet fileReferenceSet, TextRange range, int index, String text) {
    myFileReferenceSet = fileReferenceSet;
    myIndex = index;
    myRange = range;
    myText = text;
  }

  public FileReference(final FileReference original) {
    this(original.myFileReferenceSet, original.myRange, original.myIndex, original.myText);
  }

  @NotNull
  protected Collection<PsiFileSystemItem> getContexts() {
    final FileReference contextRef = getContextReference();
    if (contextRef == null) {
      return myFileReferenceSet.getDefaultContexts();
    }
    ResolveResult[] resolveResults = contextRef.multiResolve(false);
    ArrayList<PsiFileSystemItem> result = new ArrayList<PsiFileSystemItem>();
    for (ResolveResult resolveResult : resolveResults) {
      if (resolveResult.getElement() != null) {
        result.add((PsiFileSystemItem)resolveResult.getElement());
      }
    }
    return result;
  }

  @Override
  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    return ResolveCache.getInstance(getElement().getProject()).resolveWithCaching(this, MyResolver.INSTANCE, false, false);
  }

  protected ResolveResult[] innerResolve() {
    return innerResolve(getFileReferenceSet().isCaseSensitive());
  }

  protected ResolveResult[] innerResolve(boolean caseSensitive) {
    final String referenceText = getText();
    if (referenceText.isEmpty() && myIndex == 0) {
      return new ResolveResult[] { new PsiElementResolveResult(getElement().getContainingFile())};
    }
    final Collection<PsiFileSystemItem> contexts = getContexts();
    final Collection<ResolveResult> result = new THashSet<ResolveResult>(RESOLVE_RESULT_HASHING_STRATEGY);
    for (final PsiFileSystemItem context : contexts) {
      if (context != null) {
        innerResolveInContext(referenceText, context, result, caseSensitive);
      }
    }
    final int resultCount = result.size();
    return resultCount > 0 ? result.toArray(new ResolveResult[resultCount]) : ResolveResult.EMPTY_ARRAY;
  }

  protected void innerResolveInContext(@NotNull final String text, @NotNull final PsiFileSystemItem context, final Collection<ResolveResult> result,
                                       final boolean caseSensitive) {
    if (isAllowedEmptyPath(text) || ".".equals(text) || "/".equals(text)) {
      result.add(new PsiElementResolveResult(context));
    }
    else if ("..".equals(text)) {
      final PsiFileSystemItem resolved = context.getParent();
      if (resolved != null) {
        result.add(new PsiElementResolveResult(resolved));
      }
    }
    else {
      final int separatorIndex = text.indexOf('/');
      if (separatorIndex >= 0) {
        final List<ResolveResult> resolvedContexts = new ArrayList<ResolveResult>();
        if (separatorIndex == 0 /*starts with slash*/ && "/".equals(context.getName())) {
          resolvedContexts.add(new PsiElementResolveResult(context));
        }
        else {
          innerResolveInContext(text.substring(0, separatorIndex), context, resolvedContexts, caseSensitive);
        }
        final String restOfText = text.substring(separatorIndex + 1);
        for (ResolveResult contextVariant : resolvedContexts) {
          final PsiFileSystemItem item = (PsiFileSystemItem)contextVariant.getElement();
          if (item != null) {
            innerResolveInContext(restOfText, item, result, caseSensitive);
          }
        }
      }
      else {
        final String decoded = decode(text);
        if (decoded != null) {
          if (context instanceof PsiDirectory && caseSensitivityApplies((PsiDirectory)context, caseSensitive)) {
            // optimization: do not load all children into VFS
            PsiDirectory directory = (PsiDirectory)context;
            PsiFileSystemItem child = directory.findFile(decoded);
            if (child == null) child = directory.findSubdirectory(decoded);
            if (child != null) {
              result.add(new PsiElementResolveResult(getOriginalFile(child)));
            }
          }
          else {
            processVariants(context, new PsiFileSystemItemProcessor() {
              @Override
              public boolean acceptItem(String name, boolean isDirectory) {
                return caseSensitive ? decoded.equals(name) : decoded.compareToIgnoreCase(name) == 0;
              }

              @Override
              public boolean execute(@NotNull PsiFileSystemItem element) {
                result.add(new PsiElementResolveResult(getOriginalFile(element)));
                return true;
              }
            });
          }
        }
      }
    }
  }

  private static boolean caseSensitivityApplies(PsiDirectory context, boolean caseSensitive) {
    VirtualFileSystem fs = context.getVirtualFile().getFileSystem();
    return fs instanceof NewVirtualFileSystem && ((NewVirtualFileSystem)fs).isCaseSensitive() == caseSensitive;
  }

  private boolean isAllowedEmptyPath(String text) {
    return text.length() == 0 && isLast() &&
           (StringUtil.isEmpty(myFileReferenceSet.getPathString()) && myFileReferenceSet.isEmptyPathAllowed() ||
           !myFileReferenceSet.isEndingSlashNotAllowed() && myIndex > 0);
  }

  @Nullable
  public String decode(final String text) {
    // strip http get parameters
    String _text = text;
    if (text.indexOf('?') >= 0) {
      _text = text.substring(0, text.lastIndexOf('?'));
    }

    if (myFileReferenceSet.isUrlEncoded()) {
      try {
        return new URI(_text).getPath();
      }
      catch (Exception e) {
        return text;
      }
    }

    return _text;
  }

  @Override
  @NotNull
  public Object[] getVariants() {
    final String s = getText();
    if (s != null && s.equals("/")) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    final CommonProcessors.CollectUniquesProcessor<PsiFileSystemItem> collector = new CommonProcessors.CollectUniquesProcessor<PsiFileSystemItem>();
    final PsiElementProcessor<PsiFileSystemItem> processor = new PsiElementProcessor<PsiFileSystemItem>() {
      @Override
      public boolean execute(@NotNull PsiFileSystemItem fileSystemItem) {
        return new FilteringProcessor<PsiFileSystemItem>(myFileReferenceSet.getReferenceCompletionFilter(), collector).process(getOriginalFile(fileSystemItem));
      }
    };
    for (PsiFileSystemItem context : getContexts()) {
      for (final PsiElement child : context.getChildren()) {
        if (child instanceof PsiFileSystemItem) {
          processor.execute((PsiFileSystemItem)child);
        }
      }
    }
    final THashSet<PsiElement> set = new THashSet<PsiElement>(collector.getResults(), VARIANTS_HASHING_STRATEGY);
    final PsiElement[] candidates = PsiUtilCore.toPsiElementArray(set);

    final Object[] variants = new Object[candidates.length];
    for (int i = 0; i < candidates.length; i++) {
      variants[i] = createLookupItem(candidates[i]);
    }
    if (!myFileReferenceSet.isUrlEncoded()) {
      return variants;
    }
    List<Object> encodedVariants = new ArrayList<Object>(variants.length);
    for (int i = 0; i < candidates.length; i++) {
      final PsiElement element = candidates[i];
      if (element instanceof PsiNamedElement) {
        final PsiNamedElement psiElement = (PsiNamedElement)element;
        String name = psiElement.getName();
        final String encoded = encode(name, psiElement);
        if (encoded == null) continue;
        if (!encoded.equals(name)) {
          final Icon icon = psiElement.getIcon(Iconable.ICON_FLAG_READ_STATUS | Iconable.ICON_FLAG_VISIBILITY);
          LookupElementBuilder item = FileInfoManager.getFileLookupItem(candidates[i], encoded, icon);
          encodedVariants.add(item.setTailText(" (" + name + ")"));
        }
        else {
          encodedVariants.add(variants[i]);
        }
      }
    }
    return ArrayUtil.toObjectArray(encodedVariants);
  }

  protected Object createLookupItem(PsiElement candidate) {
    return FileInfoManager.getFileLookupItem(candidate);
  }

  /**
   * Converts a wrapper like WebDirectoryElement into plain PsiFile
   */
  protected static PsiFileSystemItem getOriginalFile(PsiFileSystemItem fileSystemItem) {
    final VirtualFile file = fileSystemItem.getVirtualFile();
    if (file != null && !file.isDirectory()) {
      final PsiManager psiManager = fileSystemItem.getManager();
      if (psiManager != null) {
        final PsiFile psiFile = psiManager.findFile(file);
        if (psiFile != null) {
          fileSystemItem = psiFile;
        }
      }
    }
    return fileSystemItem;
  }

  @Nullable
  protected String encode(final String name, PsiElement psiElement) {
    try {
      return new URI(null, null, name, null).toString();
    }
    catch (Exception e) {
      return name;
    }
  }

  protected static void processVariants(final PsiFileSystemItem context, final PsiFileSystemItemProcessor processor) {
    context.processChildren(processor);
  }

  @Nullable
  private FileReference getContextReference() {
    return myIndex > 0 ? myFileReferenceSet.getReference(myIndex - 1) : null;
  }

  @Override
  public PsiElement getElement() {
    return myFileReferenceSet.getElement();
  }

  @Override
  public PsiFileSystemItem resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? (PsiFileSystemItem)resolveResults[0].getElement() : null;
  }

  @Nullable
  public PsiFileSystemItem innerSingleResolve(final boolean caseSensitive) {
    final ResolveResult[] resolveResults = innerResolve(caseSensitive);
    return resolveResults.length == 1 ? (PsiFileSystemItem)resolveResults[0].getElement() : null;
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof PsiFileSystemItem)) return false;

    final PsiFileSystemItem item = resolve();
    return item != null && FileReferenceHelperRegistrar.areElementsEquivalent(item, (PsiFileSystemItem)element);
  }

  @Override
  public TextRange getRangeInElement() {
    return myRange;
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return myText;
  }

  public String getText() {
    return myText;
  }

  @Override
  public boolean isSoft() {
    return myFileReferenceSet.isSoft();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final ElementManipulator<PsiElement> manipulator = CachingReference.getManipulator(getElement());
    if (manipulator != null) {
      myFileReferenceSet.setElement(manipulator.handleContentChange(getElement(), getRangeInElement(), newElementName));
      //Correct ranges
      int delta = newElementName.length() - myRange.getLength();
      myRange = new TextRange(getRangeInElement().getStartOffset(), getRangeInElement().getStartOffset() + newElementName.length());
      FileReference[] references = myFileReferenceSet.getAllReferences();
      for (int idx = myIndex + 1; idx < references.length; idx++) {
        references[idx].myRange = references[idx].myRange.shiftRight(delta);
      }
      return myFileReferenceSet.getElement();
    }
    throw new IncorrectOperationException("Manipulator for this element is not defined: " + getElement());
  }

  public PsiElement bindToElement(@NotNull final PsiElement element, final boolean absolute) throws IncorrectOperationException {
    if (!(element instanceof PsiFileSystemItem)) throw new IncorrectOperationException("Cannot bind to element, should be instanceof PsiFileSystemItem: " + element);

    // handle empty reference that resolves to current file
    if (getCanonicalText().isEmpty() && element == getElement().getContainingFile()) return getElement();

    final PsiFileSystemItem fileSystemItem = (PsiFileSystemItem)element;
    VirtualFile dstVFile = fileSystemItem.getVirtualFile();
    if (dstVFile == null) throw new IncorrectOperationException("Cannot bind to non-physical element:" + element);

    PsiFile file = getElement().getContainingFile();
    PsiElement contextPsiFile = file.getContext();
    if (contextPsiFile != null) file = contextPsiFile.getContainingFile(); // use host file!
    final VirtualFile curVFile = file.getVirtualFile();
    if (curVFile == null) throw new IncorrectOperationException("Cannot bind from non-physical element:" + file);

    final Project project = element.getProject();

    String newName;

    if (absolute) {
      PsiFileSystemItem root = null;
      PsiFileSystemItem dstItem = null;
      for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
        if (!helper.isMine(project, dstVFile)) continue;
        PsiFileSystemItem _dstItem = helper.getPsiFileSystemItem(project, dstVFile);
        if (_dstItem != null) {
          PsiFileSystemItem _root = helper.findRoot(project, dstVFile);
          if (_root != null) {
            root = _root;
            dstItem = _dstItem;
            break;
          }
        }
      }
      if (root == null) {
        PsiFileSystemItem _dstItem = FileReferenceHelperRegistrar.NullFileReferenceHelper.INSTANCE.getPsiFileSystemItem(project, dstVFile);
        if (_dstItem != null) {
          PsiFileSystemItem _root = FileReferenceHelperRegistrar.NullFileReferenceHelper.INSTANCE.findRoot(project, dstVFile);
          if (_root != null) {
            root = _root;
            dstItem = _dstItem;
          }
        }

        if (root == null) {
          return getElement();
        }
      }

      final String relativePath = PsiFileSystemItemUtil.getRelativePath(root, dstItem);
      if (relativePath == null) {
        return getElement();
      }
      newName = myFileReferenceSet.getNewAbsolutePath(root, relativePath);

    } else { // relative path
      PsiFileSystemItem curItem = null;
      PsiFileSystemItem dstItem = null;
      final FileReferenceHelper helper = FileReferenceHelperRegistrar.getNotNullHelper(file);

      final Collection<PsiFileSystemItem> contexts = helper.getContexts(project, curVFile);
      switch (contexts.size()) {
        case 0:
          break;
        default:
          for (PsiFileSystemItem context : contexts) {
            final VirtualFile contextFile = context.getVirtualFile();
            assert contextFile != null;
            if (VfsUtilCore.isAncestor(contextFile, dstVFile, true)) {
              final String path = VfsUtilCore.getRelativePath(dstVFile, contextFile, '/');
              if (path != null) {
                return rename(path);
              }
            }
          }
        case 1:
          PsiFileSystemItem _dstItem = helper.getPsiFileSystemItem(project, dstVFile);
          PsiFileSystemItem _curItem = helper.getPsiFileSystemItem(project, curVFile);
          if (_dstItem != null && _curItem != null) {
            curItem = _curItem;
            dstItem = _dstItem;
            break;
          }
      }
      if (curItem == null) {
        throw new IncorrectOperationException("Cannot find path between files; " +
                                              "src = " + curVFile.getPresentableUrl() + "; " +
                                              "dst = " + dstVFile.getPresentableUrl() + "; " +
        "Contexts: " + contexts);
      }
      if (curItem.equals(dstItem)) {
        if (getCanonicalText().equals(dstItem.getName())) {
          return getElement();
        }
        return ElementManipulators.getManipulator(getElement()).handleContentChange(getElement(), getRangeInElement(), file.getName());
      }
      newName = PsiFileSystemItemUtil.getRelativePath(curItem, dstItem);
      if (newName == null) {
        return getElement();
      }
    }

    if (myFileReferenceSet.isUrlEncoded()) {
      newName = encode(newName, element);
    }

    return rename(newName);
  }

  /* Happens when it's been moved to another folder */
  @Override
  public PsiElement bindToElement(@NotNull final PsiElement element) throws IncorrectOperationException {
    return bindToElement(element, myFileReferenceSet.isAbsolutePathReference());
  }

  protected PsiElement rename(final String newName) throws IncorrectOperationException {
    final TextRange range = new TextRange(myFileReferenceSet.getStartInElement(), getRangeInElement().getEndOffset());
    final ElementManipulator<PsiElement> manipulator = CachingReference.getManipulator(getElement());
    if (manipulator == null) {
      throw new IncorrectOperationException("Manipulator not defined for: " + getElement());
    }
    return manipulator.handleContentChange(getElement(), range, newName);
  }

  @Override
  public void registerQuickfix(HighlightInfo info, FileReference reference) {
    for (final FileReferenceHelper helper : getHelpers()) {
      helper.registerFixes(info, reference);
    }
  }

  protected static FileReferenceHelper[] getHelpers() {
    return FileReferenceHelperRegistrar.getHelpers();
  }

  public int getIndex() {
    return myIndex;
  }

  @Override
  public String getUnresolvedMessagePattern() {
    return LangBundle.message("error.cannot.resolve")
           + " " + (isLast() ? LangBundle.message("terms.file") : LangBundle.message("terms.directory"))
           + " ''" + decode(getCanonicalText()) + "''";
  }

  public final boolean isLast() {
    return myIndex == myFileReferenceSet.getAllReferences().length - 1;
  }

  @NotNull
  public FileReferenceSet getFileReferenceSet() {
    return myFileReferenceSet;
  }

  @Override
  public LocalQuickFix[] getQuickFixes() {
    final List<LocalQuickFix> result = new ArrayList<LocalQuickFix>();
    for (final FileReferenceHelper helper : getHelpers()) {
      result.addAll(helper.registerFixes(null, this));
    }
    return result.toArray(new LocalQuickFix[result.size()]);
  }

  @Override
  public FileReference getLastFileReference() {
    return myFileReferenceSet.getLastReference();
  }

  static class MyResolver implements ResolveCache.PolyVariantResolver<FileReference> {
    static final MyResolver INSTANCE = new MyResolver();

    @Override
    public ResolveResult[] resolve(FileReference ref, boolean incompleteCode) {
      return ref.innerResolve(ref.getFileReferenceSet().isCaseSensitive());
    }
  }
}
