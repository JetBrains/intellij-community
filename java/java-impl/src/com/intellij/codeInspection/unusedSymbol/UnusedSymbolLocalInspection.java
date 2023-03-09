// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.unusedSymbol;

import com.intellij.codeInspection.options.OptDropdown;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.psi.util.AccessModifier;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.codeInspection.options.OptPane.*;

public class UnusedSymbolLocalInspection extends UnusedSymbolLocalInspectionBase {

  /**
   * @deprecated use {@link com.intellij.codeInspection.deadCode.UnusedDeclarationInspection} instead
   */
  @Deprecated
  public UnusedSymbolLocalInspection() {
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("CLASS", JavaBundle.message("inspection.unused.symbol.check.classes"),
               modifierSelector("myClassVisibility")),
      checkbox("INNER_CLASS", JavaBundle.message("inspection.unused.symbol.check.inner.classes"),
               modifierSelector("myInnerClassVisibility")),
      checkbox("FIELD", JavaBundle.message("inspection.unused.symbol.check.fields"),
               modifierSelector("myFieldVisibility")),
      checkbox("METHOD", JavaBundle.message("inspection.unused.symbol.check.methods"),
               modifierSelector("myMethodVisibility"),
               checkbox("myIgnoreAccessors", JavaBundle.message("inspection.unused.symbol.check.accessors"))),
      checkbox("PARAMETER", JavaBundle.message("inspection.unused.symbol.check.parameters"),
               modifierSelector("myParameterVisibility"),
               checkbox("myCheckParameterExcludingHierarchy",
                        JavaBundle.message("inspection.unused.symbol.check.parameters.excluding.hierarchy"))),
      checkbox("LOCAL_VARIABLE", JavaBundle.message("inspection.unused.symbol.check.localvars"))
    );
  }
  
  private static OptDropdown modifierSelector(@Language("jvm-field-name") @NotNull String bindId) {
    return dropdown(bindId, "", List.of(AccessModifier.values()),
                            AccessModifier::toPsiModifier, AccessModifier::toString);
  }
}
