// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PsiFileReference;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.javadoc.PsiSnippetAttribute;
import com.intellij.psi.javadoc.PsiSnippetAttributeValue;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PsiSnippetAttributeValueImpl extends LeafPsiElement implements PsiSnippetAttributeValue {
  public PsiSnippetAttributeValueImpl(CharSequence text) {
    super(JavaDocElementType.DOC_SNIPPET_ATTRIBUTE_VALUE, text);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSnippetAttributeValue(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiReference getReference() {
    PsiElement parent = getParent();
    if (parent instanceof PsiSnippetAttribute) {
      PsiSnippetAttribute attribute = (PsiSnippetAttribute)parent;
      String name = attribute.getName();
      if (name.equals(PsiSnippetAttribute.CLASS_ATTRIBUTE)) {
        return new SnippetFileReference(false);
      }
      else if (name.equals(PsiSnippetAttribute.FILE_ATTRIBUTE)) {
        return new SnippetFileReference(true);
      }
    }
    return null;
  }

  @Override
  public @NotNull Collection<? extends @NotNull PsiSymbolReference> getOwnReferences() {
    PsiElement parent = getParent();
    if (parent instanceof PsiSnippetAttribute) {
      PsiSnippetAttribute attribute = (PsiSnippetAttribute)parent;
      if (attribute.getName().equals(PsiSnippetAttribute.REGION_ATTRIBUTE)) {
        return Collections.singleton(JavaPsiImplementationHelper.getInstance(getProject())
                                       .getSnippetRegionSymbol(this));
      }
    }
    return super.getOwnReferences();
  }

  @Override
  public String toString() {
    return "PsiSnippetAttributeValue:" + getValue();
  }

  @Override
  public @NotNull String getValue() {
    return getValueRange().substring(getText());
  }


  @NotNull
  public TextRange getValueRange() {
    String text = getText();
    int start = 0;
    int end = text.length();
    if (text.startsWith("\"") || text.startsWith("'")) {
      start++;
    }
    if (text.endsWith("\"") || text.endsWith("'")) {
      end--;
    }
    if (end <= start) {
      start = 0;
      end = text.length();
    }
    return TextRange.create(start, end);
  }

  private class SnippetFileReference implements PsiFileReference {
    private final String mySeparator;
    private final String myExtension;
    private final VirtualFile mySnippetRoot;

    private SnippetFileReference(boolean fileRef) {
      mySeparator = fileRef ? "/" : ".";
      myExtension = fileRef ? null : "java";
      PsiFile file = getContainingFile().getOriginalFile();
      VirtualFile virtualFile = file.getVirtualFile();
      mySnippetRoot = virtualFile == null ? null : virtualFile.getParent().findChild(PsiSnippetAttribute.SNIPPETS_FOLDER);
    }

    @Override
    public @Nullable PsiElement resolve() {
      PsiFile file = getContainingFile();
      VirtualFile dir = getDirectory();
      if (dir == null) return null;
      VirtualFile targetFile;
      String lastComponent = getRangeInElement().substring(getText());
      if (myExtension == null) {
        targetFile = dir.findChild(lastComponent);
      }
      else {
        targetFile = dir.findChild(lastComponent + '.' + myExtension);
        if (targetFile == null) {
          // Extension with another case?
          targetFile = ContainerUtil.find(
            dir.getChildren(),
            f -> f.getNameWithoutExtension().equals(lastComponent) && myExtension.equalsIgnoreCase(f.getExtension()));
        }
      }
      if (targetFile == null) return null;
      return file.getManager().findFile(targetFile);
    }

    @Nullable
    private VirtualFile getDirectory() {
      if (mySnippetRoot == null) return null;
      List<String> path = StringUtil.split(getValue(), mySeparator);
      if (path.isEmpty()) return null;
      VirtualFile dir = mySnippetRoot;
      for (int i = 0; i < path.size() - 1; i++) {
        String component = path.get(i);
        dir = dir.findChild(component);
        if (dir == null) return null;
      }
      return dir;
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
      if (!(element instanceof PsiFile)) return false;
      return getElement().getManager().areElementsEquivalent(resolve(), element);
    }

    @Override
    public @NotNull PsiSnippetAttributeValue getElement() {
      return PsiSnippetAttributeValueImpl.this;
    }

    @NotNull
    @Override
    public TextRange getRangeInElement() {
      TextRange range = getValueRange();
      String text = getText();
      int lastDot = text.lastIndexOf(mySeparator);
      if (lastDot != -1) {
        return TextRange.create(lastDot + 1, range.getEndOffset());
      }
      return range;
    }

    @Override
    public @NotNull Object @NotNull [] getVariants() {
      VirtualFile directory = getDirectory();
      if (directory == null) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
      PsiManager manager = getManager();
      List<Object> list = new ArrayList<>();
      for (VirtualFile child : directory.getChildren()) {
        if (child.isDirectory()) {
          list.add(child.getName() + mySeparator);
        }
        else if (myExtension != null) {
          if (myExtension.equalsIgnoreCase(child.getExtension())) {
            list.add(child.getNameWithoutExtension());
          }
        }
        else {
          PsiFile psiFile = manager.findFile(child);
          ContainerUtil.addIfNotNull(list, psiFile);
        }
      }
      return list.toArray();
    }

    @Override
    public @NotNull @NlsSafe String getCanonicalText() {
      String text = getValue();
      return PsiSnippetAttribute.SNIPPETS_FOLDER + '/' +
             text.replace(mySeparator, "/") + (myExtension == null ? "" : "." + myExtension);
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
      PsiElement element = resolve();
      return element == null ? ResolveResult.EMPTY_ARRAY : PsiElementResolveResult.createResults(element);
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
      // TODO
      return null;
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      if (!(element instanceof PsiFile)) throw new IncorrectOperationException("PsiFile expected");
      if (isReferenceTo(element)) return PsiSnippetAttributeValueImpl.this;
      VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      String relativePath = VfsUtilCore.getRelativePath(virtualFile, mySnippetRoot, '.');
      if (relativePath == null) {
        throw new IncorrectOperationException("File not in " + mySnippetRoot + ": " + virtualFile);
      }
      if (myExtension != null) {
        if (!StringUtil.endsWithIgnoreCase(relativePath, "." + myExtension)) {
          throw new IncorrectOperationException("File name must end with ." + myExtension + ": " + virtualFile);
        }
        relativePath = relativePath.substring(0, relativePath.length() - myExtension.length() - 1);
      }
      String newText;
      if (!relativePath.contains(mySeparator)) {
        newText = relativePath;
      }
      else if (getText().startsWith("'")) {
        newText = "'" + relativePath + "'";
      }
      else {
        newText = "\"" + relativePath + "\"";
      }
      ASTNode node = getNode();
      LeafElement newNode = ChangeUtil.copyLeafWithText((LeafElement)node, newText);
      node.getTreeParent().replaceChild(node, newNode);
      return newNode.getPsi();
    }

    @Override
    public boolean isSoft() {
      return false;
    }

    @Override
    public String toString() {
      return getClass().getName() + "(" + getValue() + ")";
    }
  }
}
