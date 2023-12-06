// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeFragment;

public final class CannotCreateCodeFragmentException extends RuntimeException {
   public CannotCreateCodeFragmentException(final String reason) {
     super(reason);
   }
 }
