package com.intellij.execution.multilaunch.statistics

import com.intellij.internal.statistic.eventLog.events.*

object MultiLaunchEventFields {
  val ACTIVATE_TOOL_WINDOWS_FIELD = EventFields.Boolean("activate_tool_windows")
}

object FusExecutableRows {
  val FIELD = ObjectListEventField("rows", FusExecutionRow())
}

class FusExecutionRow: ObjectDescription() {
  var executable by field(ObjectEventField("executable", FusExecutable()))
  var condition by field(ObjectEventField("condition", FusCondition()))
  var disableDebugging by field(EventFields.Boolean("disable_debugging"))

  companion object {
    fun createData(fusExecutable: ObjectEventData, fusCondition: ObjectEventData, fusDisableDebugging: Boolean): ObjectEventData {
      return build(::FusExecutionRow) {
        executable = fusExecutable
        condition = fusCondition
        disableDebugging = fusDisableDebugging
      }
    }
  }
}

class FusExecutable: ObjectDescription() {
  var kind by field(EventFields.Enum<FusExecutableKind>("kind"))
  var typeId by field(EventFields.StringValidatedByCustomRule<ExecutableTypeIdValidationRule>("type_id"))

  companion object {
    fun createData(fusKind: FusExecutableKind, fusTypeId: String): ObjectEventData {
      return build(::FusExecutable) {
        kind = fusKind
        typeId = fusTypeId
      }
    }
  }
}

class FusCondition: ObjectDescription() {
  var typeId by field(EventFields.StringValidatedByCustomRule<ConditionTypeIdValidationRule>("type_id"))

  companion object {
    fun createData(fusTypeId: String): ObjectEventData {
      return build(::FusCondition) {
        typeId = fusTypeId
      }
    }
  }
}

object CreatedOrigin {
  val CREATED_FIELD = EventFields.Boolean("is_created")
  val ORIGIN_FIELD = EventFields.Enum<MultiLaunchCreationOrigin>("origin")
}