package com.intellij.java.injection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.impl.RedBlackTree;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public abstract class InjectedLanguageTestCase extends LightQuickFixTestCase {
  @Override
  protected void runBare(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    boolean mustCheck = !getTestName(false).contains("NoVerify") && !isStressTest();
    if (mustCheck) {
      RedBlackTree.runAssertingInternalInvariants(() -> {
        DebugUtil.runWithCheckInternalInvariantsEnabled(() -> {
          super.runBare(testRunnable);
        });
      });
    }
    else {
      super.runBare(testRunnable);
    }
  }

  @Override
  protected void setUp() throws Exception {
    try {
      DebugUtil.runWithCheckInternalInvariantsDisabled(() -> {
        super.setUp();
        new MyTestInjector(getPsiManager()).injectAll(getTestRootDisposable());
      });
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  protected @NotNull String toParseTreeText(@NotNull PsiFile file) {
    String result = JBIterable.from(file.getViewProvider().getAllFiles()).map(
        root -> DebugUtil.psiToString(root, true, false, (psiElement, consumer) ->
          InjectedLanguageManager.getInstance(getProject()).enumerate(psiElement, (injectedPsi, _) -> consumer.consume(injectedPsi))))
      .reduce(null, (r, o) -> r == null ? o : r + o);
    return Objects.requireNonNull(result);
  }
}
