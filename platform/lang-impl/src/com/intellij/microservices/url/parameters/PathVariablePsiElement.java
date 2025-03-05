// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.url.parameters;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.microservices.MicroservicesBundle;
import com.intellij.microservices.utils.CommonFakeNavigatablePomTarget;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.intellij.microservices.url.UrlConversionConstants.STANDARD_PATH_VARIABLE_NAME_PATTERN;
import static java.util.Objects.requireNonNull;

/**
 * Represents a declaration of a PathVariable - a specially marked part of the URL in the endpoint declaration:
 * EXAMPLE: in {@code "/users/{id}/delete"} it is {@code "{id}"}
 *
 * @see PathVariableDeclaringReference
 */
public class PathVariablePsiElement extends CommonFakeNavigatablePomTarget {

  private final Pattern myNameValidationPattern;
  private final PathVariablePomTarget myVariablePomTarget;
  private final boolean myForceFindUsages;

  private PathVariablePsiElement(@NotNull PathVariablePomTarget pomTarget,
                                 @NotNull Pattern nameValidationPattern,
                                 boolean forceFindUsages) {
    super(pomTarget.getScope().getProject(), pomTarget);
    myVariablePomTarget = pomTarget;
    myNameValidationPattern = nameValidationPattern;
    myForceFindUsages = forceFindUsages;
  }

  private PathVariablePsiElement(@NotNull String name,
                                 @NotNull PsiElement scope,
                                 @NotNull TextRange textRange,
                                 @NotNull Pattern nameValidationPattern,
                                 @NotNull SemDefinitionProvider semDefinitionProvider) {
    this(new PathVariablePomTarget(name, scope, textRange, semDefinitionProvider), nameValidationPattern, true);
  }

  public static PathVariablePsiElement create(@NotNull String varName,
                                              @NotNull PsiElement scope,
                                              @NotNull TextRange textRange,
                                              @NotNull SemDefinitionProvider semDefinitionProvider) {
    return new PathVariablePsiElement(varName, scope, textRange, STANDARD_PATH_VARIABLE_NAME_PATTERN, semDefinitionProvider);
  }

  public static PathVariablePsiElement create(@NotNull String varName,
                                              @NotNull PsiElement scope,
                                              @NotNull SemDefinitionProvider semDefinitionProvider) {
    return create(varName, scope, ElementManipulators.getValueTextRange(scope), semDefinitionProvider);
  }


  public static PathVariablePsiElement create(@NotNull String varName,
                                              @NotNull PsiElement scope,
                                              @NotNull TextRange textRange,
                                              @NotNull Pattern nameValidationPattern,
                                              @NotNull SemDefinitionProvider semDefinitionProvider) {
    return new PathVariablePsiElement(varName, scope, textRange, nameValidationPattern, semDefinitionProvider);
  }

  public static @Nullable PathVariablePsiElement merge(@NotNull List<PathVariablePsiElement> elements) {
    if (elements.isEmpty()) return null;
    if (elements.size() == 1) return elements.get(0);
    return new MultiplePomTarget(elements.stream().map(e -> e.getVariablePomTarget()).collect(Collectors.toSet()));
  }

  public PathVariablePomTarget getVariablePomTarget() {
    return myVariablePomTarget;
  }

  @Override
  public @NotNull String getName() { return myVariablePomTarget.getName(); }

  @Override
  public @NotNull TextRange getTextRange() { return myVariablePomTarget.getTextRange();}

  public @NotNull PathVariablePsiElement navigatingToDeclaration() {
    return new PathVariablePsiElement(myVariablePomTarget, myNameValidationPattern, false);
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (myForceFindUsages) {
      super.navigate(requestFocus);
      return;
    }
    VirtualFile file = PsiUtilCore.getVirtualFile(myVariablePomTarget.getScope());
    if(file == null) return;
    PsiNavigationSupport.getInstance()
      .createNavigatable(getProject(), file, getTextOffset())
      .navigate(requestFocus);
  }

  //TODO: Parent for a Fake PsiElement? Seriously? but `SpringBootKtWebfluxRoutingTest` relies on that
  @Override
  public @Nullable PsiElement getParent() {
    return myVariablePomTarget.getScope();
  }

  public @NotNull Pattern getNameValidationPattern() {
    return myNameValidationPattern;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    myVariablePomTarget.setName(name);
    return this;
  }

  @Override
  public int getTextOffset() {
    return requireNonNull(getParent()).getTextOffset() + myVariablePomTarget.getTextRange().getStartOffset();
  }

  @Override
  public boolean canNavigate() { return true; }

  @Override
  public int getTextLength() {
    return getName().length();
  }

  @Override
  public String getTypeName() {
    return MicroservicesBundle.message("microservices.url.path.variable.typeName");
  }

  @Override
  public Icon getIcon() {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.Variable);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PathVariablePsiElement element)) return false;

    if (!myVariablePomTarget.equals(element.myVariablePomTarget)) return false;
    if (!getName().equals(element.getName())) return false;
    if (!getTextRange().equals(element.getTextRange())) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myVariablePomTarget.hashCode();
    result = 31 * result + getName().hashCode();
    result = 31 * result + getTextRange().hashCode();
    return result;
  }

  // it is a hack for IDEA-249323 probably PathVariablePsiElement should not rely on a dedicated PsiElement and a place in it at all
  private static class MultiplePomTarget extends PathVariablePsiElement {

    private final Set<PathVariablePomTarget> myPomTargets;

    private MultiplePomTarget(Set<PathVariablePomTarget> pomTargets) {
      super(ContainerUtil.getFirstItem(pomTargets).getName(),
            ContainerUtil.getFirstItem(pomTargets).getScope(),
            ContainerUtil.getFirstItem(pomTargets).getTextRange(),
            STANDARD_PATH_VARIABLE_NAME_PATTERN, ContainerUtil.getFirstItem(pomTargets).getSemDefinitionProvider());
      this.myPomTargets = pomTargets;
    }

    @Override
    public boolean isEquivalentTo(@Nullable PsiElement another) {
      if (this == another) return true;
      if (!(another instanceof PathVariablePsiElement)) return false;
      if (another instanceof MultiplePomTarget) {
        return this.myPomTargets.equals(((MultiplePomTarget)another).myPomTargets);
      }
      PathVariablePomTarget variablePomTarget = ((PathVariablePsiElement)another).getVariablePomTarget();
      return ContainerUtil.exists(this.myPomTargets, pomTarget -> pomTarget.equals(variablePomTarget));
    }
  }
}
