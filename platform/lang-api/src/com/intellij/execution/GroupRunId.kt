// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("RunConfigurationGroupUtil")

package com.intellij.execution

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentDescriptorReusePolicy
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase

/** Provides a way to group [RunConfiguration]s to ensure that launched configurations will not reuse each others tabs
 * when shown in corresponding tool window.
 * see [com.intellij.execution.ui.RunContentManagerImpl.chooseReuseContentForDescriptor]
 * Instances of [GroupRunId] are compared using referential equality, name is for debug purposes and is not displayed to the user.
 * Run configurations that are launched together should be assigned the same instance of [GroupRunId].
 * This instance should be a new one each time the whole bunch of configurations is launched to allow reusing tabs from previous invocations.
 * see [com.intellij.execution.compound.CompoundRunConfiguration]
 **/
class GroupRunId(private val debugName: String) {
  fun createReusePolicy(name: String): RunContentDescriptorReusePolicy = CannotReuseFromSameGroupPolicy(name, this)

  private class CannotReuseFromSameGroupPolicy(
    private val contentName: String,
    private val configurationGroup: GroupRunId
  ) : RunContentDescriptorReusePolicy() {
    override fun canBeReusedBy(newDescriptor: RunContentDescriptor): Boolean {
      val other = newDescriptor.reusePolicy as? CannotReuseFromSameGroupPolicy
      val differentConfigurationsFromSameGroup = configurationGroup === other?.configurationGroup && contentName != other.contentName
      if (differentConfigurationsFromSameGroup) return false

      return DEFAULT.canBeReusedBy(newDescriptor)
    }
  }

  override fun toString(): String = "GroupRunId(id = ${hashCode()}, debugName = $debugName)"

  companion object {
    @JvmStatic
    val KEY = Key.create<GroupRunId>("RunConfiguration.GroupRunId")
  }
}


var RunConfiguration.groupRunId: GroupRunId?
  get() = (this as? UserDataHolderBase)?.getUserData(GroupRunId.KEY)
  set(id) {
    (this as? UserDataHolderBase)?.putUserData(GroupRunId.KEY, id)
  }