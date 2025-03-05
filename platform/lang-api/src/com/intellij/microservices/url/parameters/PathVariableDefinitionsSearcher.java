package com.intellij.microservices.url.parameters;

import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.util.Plow;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public interface PathVariableDefinitionsSearcher {
  boolean processDefinitions(@NotNull PsiElement context, @NotNull Processor<? super PomTargetPsiElement> processor);

  default Plow<PomTargetPsiElement> getPathVariables(PsiElement context) {
    return Plow.of(processor -> processDefinitions(context, processor));
  }
}
