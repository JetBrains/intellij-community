// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.lang.ASTNode;
import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.impl.source.javadoc.PsiSnippetAttributeValueImpl;
import com.intellij.psi.javadoc.PsiDocFragmentName;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.javadoc.PsiSnippetAttributeValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class CoreJavaPsiImplementationHelper extends JavaPsiImplementationHelper {
  private final Project myProject;

  public CoreJavaPsiImplementationHelper(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull PsiClass getOriginalClass(@NotNull PsiClass psiClass) {
    return psiClass;
  }

  @Override
  public @NotNull PsiJavaModule getOriginalModule(@NotNull PsiJavaModule module) {
    return module;
  }

  @Override
  public @NotNull PsiElement getClsFileNavigationElement(@NotNull PsiJavaFile clsFile) {
    return clsFile;
  }

  @Override
  public @NotNull LanguageLevel getEffectiveLanguageLevel(@Nullable VirtualFile virtualFile) {
    if (virtualFile != null) {
      synchronized (virtualFile) {
        LanguageLevel level = virtualFile.getUserData(FORCED_LANGUAGE_LEVEL_KEY);
        if (level != null) return level;
      }
    }
    return PsiUtil.getLanguageLevel(myProject);
  }

  public boolean setEffectiveLanguageLevel(@NotNull VirtualFile file, @Nullable LanguageLevel level) {
    synchronized (file) {
      LanguageLevel previousHolder = file.getUserData(FORCED_LANGUAGE_LEVEL_KEY);
      if (Objects.equals(previousHolder, level)) return false;
      file.putUserData(FORCED_LANGUAGE_LEVEL_KEY, level);
      return true;
    }
  }

  /**
   * The default keeps the imports in three groups, module imports, then plain imports, then static imports, and keeps a group in the
   * alphabetical order of the imported names. The result is the statement to insert the new statement before, or {@code null} for the
   * end of the list.
   */
  @Override
  public @Nullable ASTNode getDefaultImportAnchor(@NotNull PsiImportList list, @NotNull PsiImportStatementBase statement) {
    int group = importGroup(statement);
    String name = importedName(statement);
    for (PsiImportStatementBase existing : list.getAllImportStatements()) {
      int existingGroup = importGroup(existing);
      if (existingGroup > group || existingGroup == group && importedName(existing).compareTo(name) > 0) {
        return existing.getNode();
      }
    }
    return null;
  }

  private static int importGroup(@NotNull PsiImportStatementBase statement) {
    if (statement instanceof PsiImportStaticStatement) return 2;
    if (statement instanceof PsiImportStatement) return 1;
    return 0;
  }

  private static @NotNull String importedName(@NotNull PsiImportStatementBase statement) {
    PsiJavaCodeReferenceElement reference = statement.getImportReference();
    if (reference == null) return statement.getText();
    // the members of a package sort after the package itself
    return statement.isOnDemand() ? reference.getCanonicalText() + "." : reference.getCanonicalText();
  }

  /**
   * The default keeps the members in the usual order of a Java class, fields, then initializers, then constructors, then methods,
   * then inner classes, and appends the new member to its group. The result is the element to insert the new member before, or
   * {@code null} for the end of the class.
   */
  @Override
  public @Nullable PsiElement getDefaultMemberAnchor(@NotNull PsiClass psiClass, @NotNull PsiMember firstPsi) {
    PsiElement lBrace = psiClass.getLBrace();
    if (lBrace == null) return null;
    int kind = memberKind(firstPsi);
    PsiElement last = lBrace;
    for (PsiElement child = lBrace.getNextSibling(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiMember && memberKind((PsiMember)child) <= kind) {
        last = child;
      }
    }
    return PsiTreeUtil.skipWhitespacesForward(last);
  }

  private static int memberKind(@NotNull PsiMember member) {
    if (member instanceof PsiField) return 0;
    if (member instanceof PsiClassInitializer) return 1;
    if (member instanceof PsiMethod) return ((PsiMethod)member).isConstructor() ? 2 : 3;
    if (member instanceof PsiClass) return 4;
    return 3;
  }

  /**
   * The default implementation keeps the catch section as the factory created it, as there are no file templates to use.
   */
  @Override
  public void setupCatchBlock(@NotNull String exceptionName, @NotNull PsiType exceptionType, PsiElement context, @NotNull PsiCatchSection element) {
  }

  @Override
  public @NotNull PsiSymbolReference getSnippetRegionSymbol(@NotNull PsiSnippetAttributeValue value) {
    return new UnresolvedSymbolReference(value, ((PsiSnippetAttributeValueImpl)value).getValueRange());
  }

  @Override
  public @NotNull PsiSymbolReference getInheritDocSymbol(@NotNull PsiDocToken token) {
    return new UnresolvedSymbolReference(token, new TextRange(0, token.getTextLength()));
  }

  @Override
  public @Nullable PsiSymbolReference getFragmentNameSymbol(@NotNull PsiDocFragmentName token) {
    return null;
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  private static final Key<LanguageLevel> FORCED_LANGUAGE_LEVEL_KEY = Key.create("FORCED_LANGUAGE_LEVEL_KEY");

  private static final class UnresolvedSymbolReference implements PsiSymbolReference {
    private final PsiElement myElement;
    private final TextRange myRange;

    UnresolvedSymbolReference(@NotNull PsiElement element, @NotNull TextRange range) {
      myElement = element;
      myRange = range;
    }

    @Override
    public @NotNull PsiElement getElement() {
      return myElement;
    }

    @Override
    public @NotNull TextRange getRangeInElement() {
      return myRange;
    }

    @Override
    public @NotNull Collection<? extends Symbol> resolveReference() {
      return Collections.emptyList();
    }
  }
}
