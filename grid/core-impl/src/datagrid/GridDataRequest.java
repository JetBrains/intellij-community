package com.intellij.database.datagrid;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;

/**
 * @author gregsh
 */
public interface GridDataRequest extends UserDataHolder {
  @NotNull
  AsyncPromise<Void> getPromise();

  interface Context {
  }

  //todo: marker interface to reduce module dependency
  //the only usage I consider invalid
  interface DatabaseContext extends Context {
  }

  interface GridDataRequestOwner extends Disposable {
    @Nls
    @NotNull
    String getDisplayName();
  }
}
