package com.intellij.database.extractors

class ExtractionConfig(
  val addTableDdl: Boolean = false,
  val isTransposed: Boolean = false,
  val addComputedColumns: Boolean = false,
  val addGeneratedColumns: Boolean = false,
  val addColumnHeader: Boolean? = false,
  val addRowHeader: Boolean? = false,
  val addQuery: Boolean = false,
  val silent: Boolean = false,
) {
  fun toBuilder() = ExtractionConfigBuilder()
    .setAddTableDdl(addTableDdl)
    .setTransposed(isTransposed)
    .setAddComputedColumns(addComputedColumns)
    .setAddGeneratedColumns(addGeneratedColumns)
    .setAddColumnHeader(addColumnHeader)
    .setAddRowHeader(addRowHeader)
    .setSilent(silent)
}

class ExtractionConfigBuilder {
  private var addTableDdl: Boolean = false
  private var isTransposed: Boolean = false
  private var addComputedColumns: Boolean = true
  private var addGeneratedColumns: Boolean = true
  private var addColumnHeader: Boolean? = null
  private var addRowHeader: Boolean? = null
  private var addQuery: Boolean = false
  private var silent = false

  fun setAddTableDdl(value: Boolean) = apply { addTableDdl = value }
  fun setTransposed(value: Boolean) = apply { isTransposed = value }
  fun setAddComputedColumns(value: Boolean) = apply { addComputedColumns = value }
  fun setAddGeneratedColumns(value: Boolean) = apply { addGeneratedColumns = value }
  fun setAddColumnHeader(value: Boolean?) = apply { addColumnHeader = value }
  fun setAddRowHeader(value: Boolean?) = apply { addRowHeader = value }
  fun setAddQuery(value: Boolean) = apply { addQuery = value }
  fun setSilent(value: Boolean) = apply { silent = value }

  fun build() = ExtractionConfig(addTableDdl, isTransposed, addComputedColumns, addGeneratedColumns, addColumnHeader, addRowHeader, addQuery, silent)
}

fun builder() = ExtractionConfigBuilder()

@JvmField val DEFAULT_CONFIG = builder().build()

