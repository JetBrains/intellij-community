package com.intellij.database.datagrid;

import org.jetbrains.annotations.NotNull;

/**
 * @author gregsh
 */
public interface DataProducer {

  void processRequest(@NotNull GridDataRequest request);
}
