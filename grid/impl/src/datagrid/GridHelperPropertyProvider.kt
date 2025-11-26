package com.intellij.database.datagrid

import com.intellij.ide.util.PropertiesComponent

const val IS_INSIDE_NOTEBOOK_DEFAULT_VALUE: Boolean = true

interface GridHelperPropertyProvider {
  val defaultPageSizeProperty: GridHelperProperty<Int>
  val defaultLimitPageSizeProperty: GridHelperProperty<Boolean>
}

open class GridHelperPropertyProviderImpl(
  private val isInsideNotebook: Boolean = IS_INSIDE_NOTEBOOK_DEFAULT_VALUE,
): GridHelperPropertyProvider {
  override val defaultPageSizeProperty: GridHelperProperty<Int> = getDefaultPageSizeProperty()
  override val defaultLimitPageSizeProperty: GridHelperProperty<Boolean> = getDefaultLimitPageSizeProperty()

  protected fun getDefaultPageSizeProperty(
    defaultValueInsideNotebook: Int = 10,
    defaultValueOutsideNotebook: Int = 100,
  ): GridHelperProperty<Int> {
    return GridHelperIntProperty(
      getKey("datagrid.default.page.size", isInsideNotebook),
      getDefaultValue(isInsideNotebook, defaultValueInsideNotebook, defaultValueOutsideNotebook)
    )
  }

  protected fun getDefaultLimitPageSizeProperty(
    defaultValueInsideNotebook: Boolean = true,
    defaultValueOutsideNotebook: Boolean = true,
  ): GridHelperProperty<Boolean> {
    return GridHelperBooleanProperty(
      getKey("datagrid.limit.default.page.size", isInsideNotebook),
      getDefaultValue(isInsideNotebook, defaultValueInsideNotebook, defaultValueOutsideNotebook)
    )
  }
}

private class GridHelperIntProperty(
  private val key: String,
  private val defaultValue: Int,
): GridHelperProperty<Int> {
  override fun get(): Int = PropertiesComponent.getInstance().getInt(key, defaultValue)
  override fun set(value: Int) = PropertiesComponent.getInstance().setValue(key, value, defaultValue)
}

private class GridHelperBooleanProperty(
  private val key: String,
  private val defaultValue: Boolean,
): GridHelperProperty<Boolean> {
  override fun get(): Boolean = PropertiesComponent.getInstance().getBoolean(key, defaultValue)
  override fun set(value: Boolean) = PropertiesComponent.getInstance().setValue(key, value, defaultValue)
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
