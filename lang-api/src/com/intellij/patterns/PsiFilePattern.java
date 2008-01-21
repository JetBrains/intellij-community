package com.intellij.patterns;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class PsiFilePattern<T extends PsiFile, Self extends PsiFilePattern<T, Self>> extends PsiElementPattern<T, Self> {

  protected PsiFilePattern(@NotNull final NullablePatternCondition condition) {
    super(condition);
  }

  protected PsiFilePattern(final Class<T> aClass) {
    super(aClass);
  }

  public Self withParentDirectoryName(final StringPattern namePattern) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        PsiDirectory directory = t.getContainingDirectory();
        return directory != null && namePattern.getCondition().accepts(directory.getName(), matchingContext, traverseContext);
      }
    });
  }
}
