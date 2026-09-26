package com.intellij.codeInsight.lookup.impl

import com.intellij.codeInsight.lookup.LookupEx
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Helper object to manage a one-shot "prevent shrink" flag for the lookup popup.
 *
 * When a client (e.g., Rider's completion filtering) sets this flag before triggering
 * a lookup refresh, the lookup UI will capture the current popup height and use it
 * as a minimum height for that refresh cycle, preventing the popup from shrinking.
 *
 * The flag is automatically cleared after being consumed,
 * ensuring it only affects a single refresh operation.
 */
@ApiStatus.Internal
object LookupShrinkSuppressor {
  private const val KEY = "lookup.preventShrinkOnce"

  /**
   * Sets the "prevent shrink" flag on the lookup component.
   * This flag will be consumed by the next lookup refresh operation.
   *
   * @param lookup the lookup instance
   */
  @JvmStatic
  fun putPreventShrinkOnce(lookup: LookupEx) {
    val component = lookup.component
    if (component is JComponent) {
      component.putClientProperty(KEY, true)
    }
  }

  /**
   * Consumes and returns the "prevent shrink" flag from the lookup component.
   * After this call, the flag is cleared.
   *
   * @param lookup the lookup instance
   * @return `true` if the flag was set, `false` otherwise
   */
  @JvmStatic
  fun takePreventShrinkOnce(lookup: LookupEx): Boolean {
    val component = lookup.component
    if (component !is JComponent) {
      return false
    }
    val value = component.getClientProperty(KEY)
    component.putClientProperty(KEY, null)
    return value == true
  }
}
