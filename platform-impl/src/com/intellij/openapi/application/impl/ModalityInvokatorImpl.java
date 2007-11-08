/*
 * @author max
 */
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ModalityInvokator;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import org.jetbrains.annotations.NotNull;

public class ModalityInvokatorImpl implements ModalityInvokator {
  public ActionCallback invokeLater(Runnable runnable) {
    return invokeLater(runnable, Conditions.FALSE);
  }

  public ActionCallback invokeLater(final Runnable runnable, @NotNull final Condition expired) {
    return LaterInvocator.invokeLater(runnable, expired);
  }

  public ActionCallback invokeLater(final Runnable runnable, @NotNull final ModalityState state, @NotNull final Condition expired) {
    return LaterInvocator.invokeLater(runnable, state, expired);
  }

  public ActionCallback invokeLater(Runnable runnable, @NotNull ModalityState state) {
    return invokeLater(runnable, state, Conditions.FALSE);
  }  
}