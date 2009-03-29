package com.intellij.testFramework.fixtures;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author yole
 */
public interface JavaCodeInsightTestFixture extends CodeInsightTestFixture {
  JavaPsiFacade getJavaFacade();

  PsiClass addClass(@NotNull @NonNls final String classText) throws IOException;
}
