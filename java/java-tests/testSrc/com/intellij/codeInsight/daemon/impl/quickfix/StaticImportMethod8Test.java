// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInsight.daemon.impl.ImportHelperTest;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;

public class StaticImportMethod8Test extends LightQuickFixParameterizedTestCase {
  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    BooleanSupplier isInsideResolve = () -> {
      if (!ImportHelperTest.isFromJavaCodeReferenceElementResolve()) {
        return false;
      }
      Throwable currentStack = new Throwable();
      boolean isInsideStaticImportFix = ContainerUtil.exists(currentStack.getStackTrace(),
                                                             stackElement -> stackElement.getClassName().equals(StaticImportMethodFix.class.getName())
                                                                             || stackElement.getClassName().equals(StaticImportMemberFix.class.getName())
      );
      // check that resolve is called from inside StaticImport*Fix, because there are so many other intentions/fixes which doing resolve in EDT we haven't fixed yet
      return isInsideStaticImportFix;
    };
    ImportHelperTest.assertResolveNotCalledInEDTDuring(isInsideResolve, () -> {
      try {
        super.runTestRunnable(testRunnable);
      }
      catch (Throwable e) {
        ExceptionUtil.rethrow(e);
      }
    });
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/staticImportMethod8";
  }
}
