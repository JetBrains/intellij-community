// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.impl.java.stubs.impl.PsiPackageAccessibilityStatementStubImpl;
import com.intellij.psi.impl.source.PackageAccessibilityStatementElement;
import com.intellij.psi.impl.source.PsiPackageAccessibilityStatementImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

public class JavaPackageAccessibilityStatementElementType extends JavaStubElementType<PsiPackageAccessibilityStatementStub, PsiPackageAccessibilityStatement> {
  public JavaPackageAccessibilityStatementElementType(@NotNull String debugName, @NotNull IElementType parentElementType) {
    super(debugName, parentElementType);
  }

  @Override
  public PsiPackageAccessibilityStatement createPsi(@NotNull PsiPackageAccessibilityStatementStub stub) {
    return getPsiFactory(stub).createPackageAccessibilityStatement(stub);
  }

  @Override
  public PsiPackageAccessibilityStatement createPsi(@NotNull ASTNode node) {
    return new PsiPackageAccessibilityStatementImpl(node);
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new PackageAccessibilityStatementElement(this);
  }

  @NotNull
  @Override
  public PsiPackageAccessibilityStatementStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    String refText = null;
    List<String> to = new SmartList<>();

    for (LighterASTNode child : tree.getChildren(node)) {
      IElementType type = child.getTokenType();
      if (type == JavaElementType.JAVA_CODE_REFERENCE) refText = JavaSourceUtil.getReferenceText(tree, child);
      else if (type == JavaElementType.MODULE_REFERENCE) to.add(JavaSourceUtil.getReferenceText(tree, child));
    }

    return new PsiPackageAccessibilityStatementStubImpl(parentStub, this, refText, to);
  }

  @Override
  public void serialize(@NotNull PsiPackageAccessibilityStatementStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getPackageName());
    dataStream.writeUTFFast(StringUtil.join(stub.getTargets(), "/"));
  }

  @NotNull
  @Override
  public PsiPackageAccessibilityStatementStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    String packageName = dataStream.readNameString();
    List<String> targets = StringUtil.split(dataStream.readUTFFast(), "/");
    return new PsiPackageAccessibilityStatementStubImpl(parentStub, this, packageName, targets);
  }

  @Override
  public void indexStub(@NotNull PsiPackageAccessibilityStatementStub stub, @NotNull IndexSink sink) { }

  @NotNull
  public static PsiPackageAccessibilityStatement.Role typeToRole(@NotNull IElementType type) {
    if (type == JavaElementType.EXPORTS_STATEMENT) return PsiPackageAccessibilityStatement.Role.EXPORTS;
    if (type == JavaElementType.OPENS_STATEMENT) return PsiPackageAccessibilityStatement.Role.OPENS;
    throw new IllegalArgumentException("Unknown type: " + type);
  }
}