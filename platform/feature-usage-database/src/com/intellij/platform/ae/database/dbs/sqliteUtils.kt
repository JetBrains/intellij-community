package com.intellij.platform.ae.database.dbs

import com.intellij.openapi.util.io.StreamUtil
import org.jetbrains.sqlite.Binder
import org.jetbrains.sqlite.SqlitePreparedStatement
import org.jetbrains.sqlite.StatementCollection

internal fun <T : Binder> StatementCollection.prepareStatementFromFile(thisClass: IUserActivityDatabaseLayer, name: String, binder: T): SqlitePreparedStatement<T> {
  val file = "/sqlStatements/${thisClass::class.simpleName}/$name.sql"
  val stream = thisClass::class.java.getResourceAsStream(file) ?: error("Resource $file was not found")
  val textQuery = StreamUtil.readText(stream.reader()).trim()
  return prepareStatement(textQuery, binder)
}