package com.intellij.database.datagrid

import com.intellij.ide.util.propComponentProperty
import kotlin.properties.ReadWriteProperty

const val IS_INSIDE_NOTEBOOK_DEFAULT_VALUE: Boolean = true

interface GridHelperPropertyProvider {
  var defaultPageSize: Int
  var defaultLimitPageSize: Boolean
}

open class GridHelperPropertyProviderImpl(
  private val isInsideNotebook: Boolean = IS_INSIDE_NOTEBOOK_DEFAULT_VALUE,
): GridHelperPropertyProvider {
  override var defaultPageSize: Int by getDefaultPageSizeProperty()
  override var defaultLimitPageSize: Boolean by getDefaultLimitPageSizeProperty()

  protected fun getDefaultPageSizeProperty(
    defaultValueInsideNotebook: Int = 10,
    defaultValueOutsideNotebook: Int = 100,
  ): ReadWriteProperty<Any, Int> {
    return propComponentProperty(
      null,
      getKey("datagrid.default.page.size", isInsideNotebook),
      getDefaultValue(isInsideNotebook, defaultValueInsideNotebook, defaultValueOutsideNotebook)
    )
  }

  protected fun getDefaultLimitPageSizeProperty(
    defaultValueInsideNotebook: Boolean = true,
    defaultValueOutsideNotebook: Boolean = true,
  ): ReadWriteProperty<Any?, Boolean> {
    return propComponentProperty(
      null,
      getKey("datagrid.limit.default.page.size", isInsideNotebook),
      getDefaultValue(isInsideNotebook, defaultValueInsideNotebook, defaultValueOutsideNotebook)
    )
  }
}

private fun getKey(baseKey: String, isInsideNotebook: Boolean): String {
  if (isInsideNotebook) return baseKey
  return "$baseKey.big"
}

private fun <T> getDefaultValue(
  isInsideNotebook: Boolean,
  defaultValueInsideNotebook: T,
  defaultValueOutsideNotebook: T,
): T = if (isInsideNotebook) defaultValueInsideNotebook else defaultValueOutsideNotebook
