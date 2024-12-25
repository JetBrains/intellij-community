package com.intellij.microservices.url.parameters;

import com.intellij.ide.presentation.Presentation;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.microservices.MicroservicesBundle;
import com.intellij.microservices.url.references.PathVariablePresentationProvider;
import com.intellij.microservices.utils.CommonFakeNavigatablePomTarget;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.IconManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.intellij.microservices.url.UrlConversionConstants.STANDARD_PATH_VARIABLE_NAME_PATTERN;

/**
 * Represents a declaration of a PathVariable - an specially marked part of the URL in the endpoint declaration:
 * EXAMPLE: in {@code "/users/{id}/delete"} it is {@code "{id}"}
 *
 * @see PathVariableDeclaringReference
 */
public class PathVariablePsiElement extends CommonFakeNavigatablePomTarget {

  private final Pattern myNameValidationPattern;
  private final PathVariablePomTarget myVariablePomTarget;
  private final boolean myForceFindUsages;

  @Presentation(provider = PathVariablePresentationProvider.class)
  public static class PathVariablePomTarget extends CommonFakeNavigatablePomTarget.SimpleNamePomTarget {

    private PathVariablePomTarget(@NotNull String name,
                                  PsiElement scope,
                                  TextRange range,
                                  SemDefinitionProvider semDefinitionProvider) {
      super(name);
      myScope = scope;
      myTextRange = range;
      mySemDefinitionProvider = semDefinitionProvider;
    }

    private final PsiElement myScope;
    private final TextRange myTextRange;
    private final SemDefinitionProvider mySemDefinitionProvider;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      PathVariablePomTarget target = (PathVariablePomTarget)o;
      return myScope.equals(target.myScope) &&
             myTextRange.equals(target.myTextRange);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), myScope, myTextRange);
    }

    public PsiElement getScope() {
      return myScope;
    }

    public TextRange getTextRange() {
      return myTextRange;
    }

    //TODO: not sure if it really should be a method of this class
    @NotNull
    @ApiStatus.Internal
    Iterable<PsiElement> findSemDefinitionPsiElement() {
      return mySemDefinitionProvider.findSemDefiningElements(this);
    }
  }

  public interface SemDefinitionProvider {
    /**
     * Implements the PathVariable find-usages among non-literal elements (usually PsiParameters)
     *
     * @return {@link PsiElement}s for which the {@link PathVariableSem} that corresponds to the {@code pomTarget}
     * is defined via the {@link com.intellij.semantic.SemService}
     */
    @NotNull Iterable<PsiElement> findSemDefiningElements(@NotNull PathVariablePomTarget pomTarget);
  }

  private PathVariablePsiElement(@NotNull PathVariablePomTarget pomTarget,
                                 @NotNull Pattern nameValidationPattern,
                                 boolean forceFindUsages) {
    super(pomTarget.myScope.getProject(), pomTarget);
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
  public @NotNull TextRange getTextRange() { return myVariablePomTarget.myTextRange;}

  public @NotNull PathVariablePsiElement navigatingToDeclaration() {
    return new PathVariablePsiElement(myVariablePomTarget, myNameValidationPattern, false);
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (myForceFindUsages) {
      super.navigate(requestFocus);
      return;
    }
    VirtualFile file = PsiUtilCore.getVirtualFile(myVariablePomTarget.myScope);
    if(file == null) return;
    PsiNavigationSupport.getInstance()
      .createNavigatable(getProject(), file, getTextOffset())
      .navigate(requestFocus);
  }

  //TODO: Parent for a Fake PsiElement? Seriously? but `SpringBootKtWebfluxRoutingTest` relies on that
  @Override
  public @Nullable PsiElement getParent() {
    return myVariablePomTarget.myScope;
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
    return getParent().getTextOffset() + myVariablePomTarget.myTextRange.getStartOffset();
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
    return IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Variable);
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
            ContainerUtil.getFirstItem(pomTargets).myScope,
            ContainerUtil.getFirstItem(pomTargets).myTextRange,
            STANDARD_PATH_VARIABLE_NAME_PATTERN, ContainerUtil.getFirstItem(pomTargets).mySemDefinitionProvider);
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
      return this.myPomTargets.stream().anyMatch(pomTarget -> pomTarget.equals(variablePomTarget));
    }
  }
}
