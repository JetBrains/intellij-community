package com.intellij.openapi.application;

import java.lang.annotation.*;

/**
 * Add this annotation to actions (AnAction inheritors) to make them run inside a transaction.
 *
 * @see TransactionGuard
 * @since 146.*
 * @author peter
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface WrapInTransaction {

  /**
   * @return the kind of transaction to wrap the action into. By default, it's {@link TransactionKind#NO_MERGE}.
   */
  TransactionKind.Common value() default TransactionKind.Common.NO_MERGE;
}
