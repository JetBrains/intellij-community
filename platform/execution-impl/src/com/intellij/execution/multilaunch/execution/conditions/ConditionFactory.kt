package com.intellij.execution.multilaunch.execution.conditions

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.execution.multilaunch.execution.conditions.impl.ImmediatelyConditionTemplate
import com.intellij.execution.multilaunch.state.ConditionSnapshot
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
class ConditionFactory(private val project: Project) {
  companion object {
    fun getInstance(project: Project) = project.service<ConditionFactory>()
  }

  @ApiStatus.Internal
  fun create(snapshot: ConditionSnapshot): Condition? {
    val templates = ConditionTemplate.EP_NAME.extensionList.associateBy { it.type }
    val type = snapshot.type ?: return null
    val template = templates[type] ?: return null
    return template.createCondition().apply { loadAttributes(snapshot) }
  }

  fun create(template: ConditionTemplate) = template.createCondition()

  fun createDefault(): Condition = create(ImmediatelyConditionTemplate())
}