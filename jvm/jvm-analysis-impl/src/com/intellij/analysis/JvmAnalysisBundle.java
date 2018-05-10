package com.intellij.analysis;

import com.intellij.AbstractBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class JvmAnalysisBundle extends AbstractBundle {
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return ourInstance.getMessage(key, params);
  }

  @NonNls private static final String BUNDLE = "com.intellij.jvm.analysis.JvmAnalysisBundle";
  private static final JvmAnalysisBundle ourInstance = new JvmAnalysisBundle();

  private JvmAnalysisBundle() {
    super(BUNDLE);
  }
}
