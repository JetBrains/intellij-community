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
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.reference.impl.CachingReference;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiFileSystemItemProcessor;
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
import java.util.HashSet;
import java.util.List;

/**
 * @author cdr
 */
public class FileReference implements FileReferenceOwner, PsiPolyVariantReference,
                                      QuickFixProvider<FileReference>, LocalQuickFixProvider,
                                      EmptyResolveMessageProvider, BindablePsiReference {
  public static final FileReference[] EMPTY = new FileReference[0];

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

  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    final PsiManager manager = getElement().getManager();
    if (manager instanceof PsiManagerImpl) {
      return ((PsiManagerImpl)manager).getResolveCache().resolveWithCaching(this, MyResolver.INSTANCE, false, false);
    }
    return innerResolve();
  }

  protected ResolveResult[] innerResolve() {
    return innerResolve(getFileReferenceSet().isCaseSensitive());
  }

  protected ResolveResult[] innerResolve(boolean caseSensitive) {
    final String referenceText = getText();
    final TextRange range = getRangeInElement();
    if (range.isEmpty()) {
      final PsiElement element = getElement();
      final String s = element.getText();
      if (s.length() > range.getEndOffset() && s.charAt(range.getEndOffset()) == '#') {
        return new ResolveResult[] { new PsiElementResolveResult(element.getContainingFile())};
      }
    }
    final Collection<PsiFileSystemItem> contexts = getContexts();
    final Collection<ResolveResult> result = new HashSet<ResolveResult>(contexts.size());
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
          processVariants(context, new PsiFileSystemItemProcessor() {
            public boolean acceptItem(String name, boolean isDirectory) {
              return caseSensitive ? decoded.equals(name) : decoded.compareToIgnoreCase(name) == 0;
            }

            public boolean execute(PsiFileSystemItem element) {
              result.add(new PsiElementResolveResult(getOriginalFile(element)));
              return false;
            }
          });
        }
      }
    }
  }

  private boolean isAllowedEmptyPath(String text) {
    return text.length() == 0 && isLast() &&
           (StringUtil.isEmpty(myFileReferenceSet.getPathString()) && myFileReferenceSet.isEmptyPathAllowed() ||
           !myFileReferenceSet.isEndingSlashNotAllowed() && myIndex > 0);
  }

  @Nullable
  private String decode(final String text) {
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

  @NotNull
  public Object[] getVariants() {
    final String s = getText();
    if (s != null && s.equals("/")) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    final CommonProcessors.CollectUniquesProcessor<PsiElement> collector = new CommonProcessors.CollectUniquesProcessor<PsiElement>();
    final PsiElementProcessor<PsiFileSystemItem> processor = new PsiElementProcessor<PsiFileSystemItem>() {
      public boolean execute(PsiFileSystemItem fileSystemItem) {
        return new FilteringProcessor<PsiElement>(myFileReferenceSet.createCondition(), collector).process(getOriginalFile(fileSystemItem));
      }
    };
    for (PsiFileSystemItem context : getContexts()) {
      for (final PsiElement child : context.getChildren()) {
        if (child instanceof PsiFileSystemItem) {
          processor.execute((PsiFileSystemItem)child);
        }
      }
    }
    final THashSet<PsiElement> set = new THashSet<PsiElement>(collector.getResults(), new TObjectHashingStrategy<PsiElement>() {
      public int computeHashCode(final PsiElement object) {
        if (object instanceof PsiNamedElement) {
          final String name = ((PsiNamedElement)object).getName();
          if (name != null) {
            return name.hashCode();
          }
        }
        return object.hashCode();
      }

      public boolean equals(final PsiElement o1, final PsiElement o2) {
        if (o1 instanceof PsiNamedElement && o2 instanceof PsiNamedElement) {
          return Comparing.equal(((PsiNamedElement)o1).getName(), ((PsiNamedElement)o2).getName());
        }
        return o1.equals(o2);
      }
    });
    final PsiElement[] candidates = set.toArray(new PsiElement[set.size()]);

    final Object[] variants = new Object[candidates.length];
    for (int i = 0; i < candidates.length; i++) {
      variants[i] = createLookupItem(candidates[i]);
    }

    if (myFileReferenceSet.isUrlEncoded()) {
      for (int i = 0; i < candidates.length; i++) {
        final PsiElement element = candidates[i];
        if (element instanceof PsiNamedElement) {
          final PsiNamedElement psiElement = (PsiNamedElement)element;
          String name = psiElement.getName();
          final String encoded = encode(name);
          if (!encoded.equals(name)) {
            final Icon icon = psiElement.getIcon(Iconable.ICON_FLAG_READ_STATUS | Iconable.ICON_FLAG_VISIBILITY);
            variants[i] = FileInfoManager.getFileLookupItem(candidates[i], encoded, icon);
          }
        }
      }
    }
    return variants;
  }

  protected Object createLookupItem(PsiElement candidate) {
    return FileInfoManager.getFileLookupItem(candidate);
  }

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

  private static String encode(final String name) {
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

  public PsiElement getElement() {
    return myFileReferenceSet.getElement();
  }

  public PsiFileSystemItem resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? (PsiFileSystemItem)resolveResults[0].getElement() : null;
  }

  @Nullable
  public PsiFileSystemItem innerSingleResolve(final boolean caseSensitive) {
    final ResolveResult[] resolveResults = innerResolve(caseSensitive);
    return resolveResults.length == 1 ? (PsiFileSystemItem)resolveResults[0].getElement() : null;
  }

  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof PsiFileSystemItem)) return false;

    final PsiFileSystemItem item = resolve();
    return item != null && FileReferenceHelperRegistrar.areElementsEquivalent(item, (PsiFileSystemItem)element);
  }

  public TextRange getRangeInElement() {
    return myRange;
  }

  @NotNull
  public String getCanonicalText() {
    return myText;
  }

  public String getText() {
    return myText;
  }

  public boolean isSoft() {
    return myFileReferenceSet.isSoft();
  }

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

    final PsiFileSystemItem fileSystemItem = (PsiFileSystemItem)element;
    VirtualFile dstVFile = fileSystemItem.getVirtualFile();
    if (dstVFile == null) throw new IncorrectOperationException("Cannot bind to non-physical element:" + element);

    PsiFile file = getElement().getContainingFile();
    if (file.getContext() != null) file = file.getContext().getContainingFile(); // use host file!
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
          return element;
        }
      }

      final String relativePath = PsiFileSystemItemUtil.getRelativePath(root, dstItem);
      if (relativePath == null) {
        return element;
      }
      newName = myFileReferenceSet.absoluteUrlNeedsStartSlash() ? "/" + relativePath : relativePath  ;

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
            if (VfsUtil.isAncestor(contextFile, dstVFile, true)) {
              final String path = VfsUtil.getRelativePath(dstVFile, contextFile, '/');
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
        return element;
      }
    }

    if (myFileReferenceSet.isUrlEncoded()) {
      newName = encode(newName);
    }

    return rename(newName);
  }

  /* Happens when it's been moved to another folder */
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

  public String getUnresolvedMessagePattern() {
    final StringBuilder builder = new StringBuilder(LangBundle.message("error.cannot.resolve"));
    builder.append(" ").append(isLast() ? LangBundle.message("terms.file") : LangBundle.message("terms.directory"));
    builder.append(" ''{0}''");
    return builder.toString();
  }

  public final boolean isLast() {
    return myIndex == myFileReferenceSet.getAllReferences().length - 1;
  }

  @NotNull
  public FileReferenceSet getFileReferenceSet() {
    return myFileReferenceSet;
  }

  public LocalQuickFix[] getQuickFixes() {
    final List<LocalQuickFix> result = new ArrayList<LocalQuickFix>();
    for (final FileReferenceHelper helper : getHelpers()) {
      result.addAll(helper.registerFixes(null, this));
    }
    return result.toArray(new LocalQuickFix[result.size()]);
  }

  public FileReference getLastFileReference() {
    return myFileReferenceSet.getLastReference();
  }

  static class MyResolver implements ResolveCache.PolyVariantResolver<FileReference> {
    static final MyResolver INSTANCE = new MyResolver();

    public ResolveResult[] resolve(FileReference ref, boolean incompleteCode) {
      return ref.innerResolve(ref.getFileReferenceSet().isCaseSensitive());
    }
  }
}
