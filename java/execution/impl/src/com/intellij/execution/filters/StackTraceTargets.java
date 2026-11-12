// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.execution.filters.ExceptionWorker.ParsedLine;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The code that the frames of a stack trace point to, found the way the console finds it when a frame link is followed,
 * without an editor.
 */
@ApiStatus.Internal
public final class StackTraceTargets {
  private StackTraceTargets() {
  }

  /**
   * @param element the target, or the element whose file holds {@code range}
   * @param range   the range in the file of {@code element}, or {@code null} for the declaration {@code element} itself
   */
  public record Target(@NotNull PsiElement element, @Nullable TextRange range) {
  }

  /**
   * @param lines        the lines of a stack trace, the exception messages included: a frame is refined by the line above it
   * @param resolvesLine the lines to find the targets of; the line above each of them is read too, to refine it
   * @return the targets of each line, empty for a line that is not a frame, whose class is not found or that
   * {@code resolvesLine} rejects
   */
  @RequiresReadLock
  public static @NotNull List<List<Target>> find(@NotNull Project project,
                                                 @NotNull GlobalSearchScope scope,
                                                 @NotNull List<String> lines,
                                                 @NotNull Predicate<String> resolvesLine) {
    ExceptionFilter filter = new ExceptionFilter(project, scope);
    ExceptionInfoCache cache = new ExceptionInfoCache(project, scope);
    List<List<Target>> targetsByLine = new ArrayList<>();
    int lastReadIndex = -1;
    for (int index = 0; index < lines.size(); index++) {
      String line = lines.get(index);
      if (!resolvesLine.test(line)) {
        targetsByLine.add(List.of());
        continue;
      }
      if (index > 0 && lastReadIndex != index - 1) {
        String lineAbove = lines.get(index - 1);
        filter.applyFilter(lineAbove, lineAbove.length());
      }
      lastReadIndex = index;
      ExceptionLineRefiner refiner = filter.getLocationRefiner();
      if (filter.applyFilter(line, line.length()) == null) {
        targetsByLine.add(List.of());
        continue;
      }
      ParsedLine parsedLine = ExceptionWorker.parseExceptionLine(line);
      if (parsedLine == null) {
        targetsByLine.add(List.of());
        continue;
      }
      String className = parsedLine.classFqnRange.substring(line).trim();
      String methodName = parsedLine.methodNameRange.substring(line);
      Map<VirtualFile, PsiElement> classes = cache.resolveClassOrFile(className, parsedLine.fileName).getClasses();
      List<Target> targets = new ArrayList<>();
      for (PsiElement declaration : classes.values()) {
        targets.addAll(findInFile(declaration, className, methodName, parsedLine.lineNumber - 1, refiner));
      }
      targetsByLine.add(targets);
    }
    return targetsByLine;
  }

  private static @NotNull List<Target> findInFile(@NotNull PsiElement declaration,
                                                  @NotNull String className,
                                                  @NotNull String methodName,
                                                  int line,
                                                  @Nullable ExceptionLineRefiner refiner) {
    PsiFile file = declaration.getContainingFile();
    Document document = file == null ? null : PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (line < 0 || document == null || file instanceof PsiCompiledElement || line >= document.getLineCount()) {
      return List.of(new Target(declaration, null));
    }
    int lineStart = document.getLineStartOffset(line);
    int lineEnd = document.getLineEndOffset(line);
    CharSequence text = document.getCharsSequence();
    int start = lineStart;
    while (start < lineEnd && StringUtil.isWhiteSpace(text.charAt(start))) start++;
    int end = lineEnd;
    while (end > start && StringUtil.isWhiteSpace(text.charAt(end - 1))) end--;
    int codeStart = start;
    if (refiner != null) {
      Set<ExceptionLineRefiner.RefinerMatchResult> matches =
        ExceptionLineParserImpl.ExceptionFinder.getExceptionOrigin(file, refiner, lineStart, lineEnd);
      if (matches.size() == 1) {
        PsiElement target = matches.iterator().next().target();
        return List.of(new Target(target, target.getTextRange()));
      }
      if (matches.isEmpty()) {
        Collection<PsiElement> divergedTargets =
          FindDivergedExceptionLineHandler.findDivergedTargets(file, className, methodName, refiner, lineStart, lineEnd);
        // a target that holds the line is its method or initializer: the code of the line, such as a for-each loop, hides the call
        if (divergedTargets != null && !divergedTargets.isEmpty() &&
            !ContainerUtil.exists(divergedTargets, target -> target.getTextRange().contains(codeStart))) {
          return ContainerUtil.map(divergedTargets, target -> target instanceof PsiMethod ? new Target(target, null) : new Target(target, target.getTextRange()));
        }
      }
    }
    return List.of(new Target(file, new TextRange(start, end)));
  }
}
