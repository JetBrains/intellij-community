// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.*;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.StackOverflowPreventedException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FileElement extends LazyParseableElement implements FileASTNode {
  public static final FileElement[] EMPTY_ARRAY = new FileElement[0];
  private volatile CharTable myCharTable = new CharTableImpl();
  private volatile boolean myDetached;
  private volatile AstSpine myStubbedSpine;

  @Override
  protected PsiElement createPsiNoLock() {
    return myDetached ? null : super.createPsiNoLock();
  }

  public void detachFromFile() {
    myDetached = true;
    clearPsi();
  }

  @Override
  public @NotNull CharTable getCharTable() {
    return myCharTable;
  }

  @Override
  public @NotNull LighterAST getLighterAST() {
    if (!isParsed()) {
      LightLanguageStubDefinition lightStubFactory = getLightStubFactory();
      if (lightStubFactory != null) {
        FlyweightCapableTreeStructure<@NotNull LighterASTNode> structure = lightStubFactory.parseContentsLight(this);
        return new FCTSBackedLighterAST(getCharTable(), structure);
      }
    }
    return new TreeBackedLighterAST(this);
  }

  private @Nullable LightLanguageStubDefinition getLightStubFactory() {
    Language language = getElementType().getLanguage();
    LanguageStubDescriptor stubDescriptor = StubElementRegistryService.getInstance().getStubDescriptor(language);
    if (stubDescriptor == null) {
      return null;
    }

    LanguageStubDefinition stubDefinition = stubDescriptor.getStubDefinition();
    if (!(stubDefinition instanceof LightLanguageStubDefinition)) {
      return null;
    }

    return (LightLanguageStubDefinition)stubDefinition;
  }

  public FileElement(@NotNull IElementType type, CharSequence text) {
    super(type, text);
  }

  @Override
  public PsiManagerEx getManager() {
    CompositeElement treeParent = getTreeParent();
    if (treeParent != null) return treeParent.getManager();
    PsiElement psi = getPsi();
    if (psi == null) throw PsiInvalidElementAccessException.createByNode(this, null);
    return (PsiManagerEx)psi.getManager();
  }

  @Override
  public ASTNode copyElement() {
    PsiFileImpl psiElement = (PsiFileImpl)getPsi();
    PsiFileImpl psiElementCopy = (PsiFileImpl)psiElement.copy();
    return psiElementCopy.getTreeElement();
  }

  public void setCharTable(@NotNull CharTable table) {
    myCharTable = table;
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myStubbedSpine = null;
  }

  @ApiStatus.Internal
  public final @NotNull AstSpine getStubbedSpine() {
    AstSpine result = myStubbedSpine;
    if (result == null) {
      PsiFileImpl file = (PsiFileImpl)getPsi();
      LanguageStubDescriptor descriptor = file.getStubDescriptor();
      if (descriptor == null) return AstSpine.EMPTY_SPINE;

      result = RecursionManager.doPreventingRecursion(file, false, () -> {
        return new AstSpine(calcStubbedDescendants(descriptor.getStubDefinition().getBuilder()));
      });

      if (result == null) {
        throw new StackOverflowPreventedException("Endless recursion prevented");
      }
      myStubbedSpine = result;
    }
    return result;
  }

  private List<CompositeElement> calcStubbedDescendants(StubBuilder builder) {
    List<CompositeElement> result = new ArrayList<>();
    result.add(this);

    acceptTree(new RecursiveTreeElementWalkingVisitor() {
      @Override
      public void visitComposite(CompositeElement node) {
        CompositeElement parent = node.getTreeParent();
        if (parent != null && builder.skipChildProcessingWhenBuildingStubs(parent, node)) {
          return;
        }

        IElementType type = node.getElementType();
        StubElementFactory<?, ?> factory = StubElementRegistryService.getInstance().getStubFactory(type);
        if (factory != null && factory.shouldCreateStub(node)) {
          result.add(node);
        }

        super.visitNode(node);
      }
    });
    return result;
  }

}
