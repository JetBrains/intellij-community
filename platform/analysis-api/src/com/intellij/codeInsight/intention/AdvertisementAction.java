package com.intellij.codeInsight.intention;


import org.jetbrains.annotations.NotNull;

/**
 * Marker interface for {@link IntentionAction intentions} that advertise some actions. For example, AI Actions.
 * Marked actions are shown at the bottom of the list of available intentions.
 *
 * @author Mikhail Senkov
 */
public interface AdvertisementAction extends PriorityAction {
  @Override
  default @NotNull Priority getPriority() {
    return Priority.BOTTOM;
  }
}
