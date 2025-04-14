package com.intellij.database.connection.throwable.info;

import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ErrorInfo extends ThrowableInfo {
  @NotNull
  List<Fix> getFixes();


  interface Fix {
    @Nls
    String getName();
    default Integer getMnemonic() { return null; }
    default boolean isSilent() {
      return true;
    }
    void apply(@NotNull DataContext dataContext);
  }
}
