package com.intellij.codeInsight.intention;


import org.jetbrains.annotations.NotNull;

public interface AdvertisementAction extends PriorityAction {
  @Override
  default @NotNull Priority getPriority() {
    return Priority.BOTTOM;
  }
}
