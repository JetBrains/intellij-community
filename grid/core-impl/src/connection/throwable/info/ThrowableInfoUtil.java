package com.intellij.database.connection.throwable.info;

import com.intellij.database.datagrid.GridUtilCore;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class ThrowableInfoUtil {

  private ThrowableInfoUtil() {
  }

  public static @Nls @NotNull String getDefaultMessage(@NotNull Throwable throwable) {
    return GridUtilCore.getLongMessage(throwable);
  }

  public static @NotNull Throwable getActualThrowable(@NotNull ThrowableInfo info) {
    String message = info.getMessage();
    Throwable originalThrowable = info.getOriginalThrowable();
    return originalThrowable != null && getDefaultMessage(originalThrowable).equals(message)
           ? originalThrowable
           : new RuntimeException(message, originalThrowable);
  }

  public static List<ErrorInfo.Fix> getAllFixes(@NotNull ErrorInfo error) {
    List<ErrorInfo.Fix> fixes = new ArrayList<>(error.getFixes());
    for (RuntimeErrorActionProvider provider : RuntimeErrorActionProvider.getProviders()) {
      ErrorInfo.Fix fix = provider.createAction(error);
      if (fix != null) fixes.add(fix);
    }
    return fixes;
  }
}
