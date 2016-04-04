/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.application;

import java.lang.annotation.*;

/**
 * Add this annotation to modal dialogs (DialogWrapper inheritors) shown from within transactions,
 * to allow nested transactions inside those dialogs.
 *
 * @see TransactionGuard#acceptNestedTransactions(TransactionKind...)
 * @since 146.*
 * @author peter
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface AcceptNestedTransactions {

  /**
   * @return the kinds of transaction to allow inside the dialog
   */
  TransactionKind.Common[] value();
}
