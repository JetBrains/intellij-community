// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.addingPolicy

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PolicyObeyingResultSet
import org.jetbrains.annotations.ApiStatus
import com.intellij.util.containers.Stack

/**
 * An intermediary instance, that controls the policy of in which manner
 * should completion results be added to the [originalResult]
 */
@ApiStatus.Internal
class PolicyController(protected val originalResult: CompletionResultSet) : () -> ElementsAddingPolicy {
  private val policies: Stack<ElementsAddingPolicy> = Stack()

  /**
   * Make the [policy] rule how elements are added to the [originalResult]
   * If there is already an active policy A in the controller, than it
   * will be put on the stack. So that when the newly added policy will
   * be popped, the policy A will be in action again.
   *
   * @see popPolicy
   */
  private fun pushPolicy(policy: ElementsAddingPolicy) {
    policies.push(policy)
    policy.onActivate(originalResult)
  }

  /**
   * Revoke currently active policy
   *
   * @throws NoActivePolicyException if there is no active policy
   * @see [pushPolicy]
   */
  private fun popPolicy() {
    verifyNotEmptyStack()
    val policyToDeactivate = policies.pop()
    policyToDeactivate.onDeactivate(originalResult)
  }

  /**
   * @return A result set, that will be obeying to this controller
   */
  fun getObeyingResultSet(): CompletionResultSet {
    return PolicyObeyingResultSet(originalResult, this)
  }

  /**
   * Invoke the given action
   */
  fun <T> invokeWithPolicy(policy: ElementsAddingPolicy, action: () -> T): T {
    pushPolicy(policy)
    try {
      return action()
    }
    finally {
      popPolicy()
    }
  }

  override fun invoke(): ElementsAddingPolicy {
    verifyNotEmptyStack()
    return policies.peek()!!
  }

  private fun verifyNotEmptyStack() {
    if (policies.isEmpty()) {
      throw NoActivePolicyException()
    }
  }

  public class NoActivePolicyException : Exception("ElementsAddingPolicyController does not have an active policy")
}