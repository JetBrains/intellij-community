// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages

import com.intellij.ide.util.scopeChooser.ScopeIdMapper
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object JavaFindUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("java.find.usages", 3)

  @JvmField
  val USAGES: BooleanEventField = EventFields.Boolean("usages")

  @JvmField
  val TEXT_OCCURRENCES: BooleanEventField = EventFields.Boolean("textOccurrences")

  @JvmField
  val SEARCH_SCOPE: StringEventField = EventFields.String("searchScope", ScopeIdMapper.standardNames.toList())

  @JvmField
  val METHOD_USAGES: BooleanEventField = EventFields.Boolean("methodUsages")

  @JvmField
  val FIELD_USAGES: BooleanEventField = EventFields.Boolean("fieldUsages")

  @JvmField
  val DERIVED_USAGES: BooleanEventField = EventFields.Boolean("derivedUsages")

  @JvmField
  val IMPLEMENTING_CLASSES: BooleanEventField = EventFields.Boolean("implementingClasses")

  @JvmField
  val DERIVED_INTERFACES: BooleanEventField = EventFields.Boolean("derivedInterfaces")

  @JvmField
  val SEARCH_FOR_BASE_METHODS: BooleanEventField = EventFields.Boolean("searchForBaseMethods")

  @JvmField
  val OVERRIDING_METHODS: BooleanEventField = EventFields.Boolean("overridingMethods")

  @JvmField
  val IMPLEMENTING_METHODS: BooleanEventField = EventFields.Boolean("implementingMethods")

  @JvmField
  val INCLUDE_INHERITED: BooleanEventField = EventFields.Boolean("includeInherited")

  @JvmField
  val INCLUDE_OVERLOAD: BooleanEventField = EventFields.Boolean("includeOverload")

  @JvmField
  val IMPLICIT_CALLS: BooleanEventField = EventFields.Boolean("implicitCalls")

  @JvmField
  val CLASSES_USAGES: BooleanEventField = EventFields.Boolean("classesUsages")

  @JvmField
  val SEARCH_FOR_BASE_ACCESSOR: BooleanEventField = EventFields.Boolean("searchForBaseAccessors")

  @JvmField
  val SEARCH_FOR_ACCESSORS: BooleanEventField = EventFields.Boolean("searchForAccessors")

  @JvmField
  val SEARCH_IN_OVERRIDING: BooleanEventField = EventFields.Boolean("searchInOverriding")

  @JvmField
  val READ_ACCESS: BooleanEventField = EventFields.Boolean("readAccess")

  @JvmField
  val WRITE_ACCESS: BooleanEventField = EventFields.Boolean("writeAccess")

  @JvmField
  val FIND_CLASS_STARTED: VarargEventId = registerEvent("find.class.started",
                                                        METHOD_USAGES,
                                                        FIELD_USAGES,
                                                        DERIVED_USAGES,
                                                        IMPLEMENTING_CLASSES,
                                                        DERIVED_INTERFACES)

  @JvmField
  val FIND_METHOD_STARTED: VarargEventId = registerEvent("find.method.started",
                                                         SEARCH_FOR_BASE_METHODS,
                                                         OVERRIDING_METHODS,
                                                         IMPLEMENTING_METHODS,
                                                         INCLUDE_INHERITED,
                                                         INCLUDE_OVERLOAD,
                                                         IMPLICIT_CALLS)

  @JvmField
  val FIND_PACKAGE_STARTED: VarargEventId = registerEvent("find.package.started", CLASSES_USAGES)

  @JvmField
  val FIND_THROW_STARTED: VarargEventId = registerEvent("find.throw.started")

  @JvmField
  val FIND_VARIABLE_STARTED: VarargEventId = registerEvent("find.variable.started",
                                                           SEARCH_FOR_BASE_ACCESSOR,
                                                           SEARCH_FOR_ACCESSORS,
                                                           SEARCH_IN_OVERRIDING,
                                                           READ_ACCESS,
                                                           WRITE_ACCESS)

  private fun registerEvent(eventId: String, vararg additionalFields: EventField<*>): VarargEventId {
    return GROUP.registerVarargEvent(eventId, USAGES, TEXT_OCCURRENCES, SEARCH_SCOPE, *additionalFields)
  }
}