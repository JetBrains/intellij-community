package com.intellij.codeInspection;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastContextKt;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class JvmAnalysisTestsUastUtil {
  public static <T extends UElement> Set<T> getUElementsOfTypeFromFile(@NotNull PsiFile file, @NotNull Class<T> type) {
    return getUElementsOfTypeFromFile(file, type, null);
  }

  public static <T extends UElement> Set<T> getUElementsOfTypeFromFile(@NotNull PsiFile file,
                                                                       @NotNull Class<T> type,
                                                                       @Nullable Predicate<T> filter) {
    Set<T> result = new HashSet<>();
    PsiTreeUtil.processElements(file, new PsiElementProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element) {
        T uElement = UastContextKt.toUElement(element, type);
        if (uElement != null && (filter == null || filter.test(uElement))) {
          result.add(uElement);
        }
        return true;
      }
    });
    return result;
  }
}
