// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb.rql

import com.jetbrains.rhizomedb.*

data class QueryResult(val columnLabels: List<String>, val rows: List<List<Any?>>)

fun executeQuery(db: DB, query: String): QueryResult {
  val parsedQuery = Parser(query).parse()
  return db.select(parsedQuery)
}

private typealias Row = Map<String, Any?>

private fun DB.allTables() = queryIndex(IndexQuery.Column(EntityType.Ident.attr as Attribute<String>))

private fun DB.rows(table: Datom): List<Row> {
  val rows = queryIndex(IndexQuery.LookupMany(Entity.Type.attr as Attribute<EID>, table.eid))
  return rows.map { row ->
    listOf(table.value.toString() + ".eid" to row.eid) +
    queryIndex(IndexQuery.Entity(row.eid))
      .filterNot { it.attr == Entity.Type.attr }
      .map { datom ->
        val name = table.value.toString() + "." + displayAttribute(datom.attr).split("/").last()
        val value = datom.value
        name to value
      }
  }.map { row ->
    row.toMap()
  }
}

private fun DB.select(query: Query): QueryResult {
  val tables = allTables()

  val fromTables = query.from.map { source ->
    when (source) {
      is FromSource.Join -> TODO()
      is FromSource.Table -> {
        val target = source.name.lexeme
        val matchedTables = tables.filter {
          val name = it.value.toString().split(":").first()
          name == target || name.endsWith(".$target")
        }
        when (matchedTables.size) {
          0 -> runtimeError("Unknown Entity '$target'", source.name)
          1 -> matchedTables.first()
          else -> {
            val tableNames = tables.joinToString("\n") { it.value.toString() }
            runtimeError("Entity '$target' is ambiguous: $matchedTables\n\n$tableNames", source.name)
          }
        }
      }
    }
  }

  val rowValues = when (fromTables.size) {
    0 -> listOf(emptyMap())
    1 -> rows(fromTables.first())
    else -> cartesianProduct(fromTables.map { table -> rows(table) })
  }

  return when (val select = query.select) {
    is ColumnSelection.Wildcard -> {
      val columnLabels = rowValues.flatMap { it.keys }.distinct()
      val values = applyWhere(rowValues, query.where).map { row -> columnLabels.map { c -> row[c] } }
      val prefix = commonPrefix(columnLabels)
      QueryResult(columnLabels.map { it.removePrefix(prefix) }, values)
    }
    is ColumnSelection.Columns -> {
      val columnLabels = select.columns.map { col -> col.alias ?: col.expression.label() }
      val values = applyWhere(rowValues, query.where).map { row ->
        select.columns.map { c -> resolveExpression(row, c.expression) }
      }
      QueryResult(columnLabels, values)
    }
  }
}

private fun DB.applyWhere(rows: List<Row>, where: Expression?): List<Row> {
  return rows.filter { row ->
    if (where != null) {
      resolveExpression(row, where).truthy()
    }
    else {
      true
    }
  }
}

private fun Expression.label(): String {
  return when (this) {
    is Expression.Literal -> value.literal.toString()
    is Expression.Variable -> name.lexeme
    is Expression.UnaryOp -> "(${operator.lexeme}${operand.label()})"
    is Expression.BinaryOp -> "(${left.label()} ${operator.lexeme} ${right.label()})"
    is Expression.InOp -> "(${left.label()} IN ...)"
    is Expression.FunctionCall -> "${expression.label()}(...)"
  }
}

private fun DB.resolveExpression(row: Row, expr: Expression): Any? {
  return when (expr) {
    is Expression.Literal -> expr.value.literal
    is Expression.Variable -> resolveVariable(row, expr)
    is Expression.UnaryOp -> resolveUnaryOp(row, expr)
    is Expression.BinaryOp -> resolveBinaryOp(row, expr)
    is Expression.InOp -> resolveIn(row, expr)
    is Expression.FunctionCall -> resolveFunction(row, expr)
  }
}

private fun DB.resolveVariable(row: Row, variable: Expression.Variable): Any? {
  val name = variable.name.lexeme
  if (name in row) {
    return row[name]
  }
  val suffixMatches = row.keys.filter { c ->
    val column = c.split(".").joinToString(".") { it.split(":").first() }
    column == name || column.endsWith(".$name")
  }
  if (suffixMatches.size == 1) {
    return row[suffixMatches.first()]
  }
  runtimeError("Unknown column '$name'", variable.name)
}

private fun DB.resolveUnaryOp(row: Row, expr: Expression.UnaryOp): Any {
  return when (expr.operator.type) {
    TokenType.MINUS -> {
      when (val operand = resolveExpression(row, expr.operand)) {
        is Number -> -(operand.toDouble())
        else -> runtimeError("Operand is not numeric", expr.operand)
      }
    }
    TokenType.PLUS -> when (val operand = resolveExpression(row, expr.operand)) {
      is Number -> operand.toDouble()
      else -> runtimeError("Operan is not numeric", expr.operand)
    }
    TokenType.NOT -> when (val operand = resolveExpression(row, expr.operand)) {
      is Boolean -> !operand
      else -> runtimeError("Operand is not boolean", expr.operand)
    }
    else -> runtimeError("Unknown operator", expr.operator)
  }
}

private fun DB.resolveBinaryOp(row: Row, expr: Expression.BinaryOp): Any {
  val left = resolveExpression(row, expr.left)
  val right = resolveExpression(row, expr.right)
  return when (expr.operator.type) {
    TokenType.EQUAL -> normalize(left) == normalize(right)
    TokenType.NOT_EQUAL -> normalize(left) != normalize(right)
    TokenType.LIKE -> {
      if (right is String) {
        patternToRegex(right).matches(left.toString())
      }
      else {
        runtimeError("IN operator requires a pattern string", expr.right)
      }
    }
    TokenType.OR -> when {
      left !is Boolean -> runtimeError("Operand are not boolean", expr.left)
      right !is Boolean -> runtimeError("Operand are not boolean", expr.right)
      else -> left || right
    }
    TokenType.AND -> when {
      left !is Boolean -> runtimeError("Operand are not boolean", expr.left)
      right !is Boolean -> runtimeError("Operand are not boolean ${expr.right.label()}", expr.right)
      else -> left && right
    }
    TokenType.GREATER -> when {
      left !is Number -> runtimeError("Operand are not boolean", expr.left)
      right !is Number -> runtimeError("Operand are not boolean", expr.right)
      else -> left.toDouble() > right.toDouble()
    }
    TokenType.GREATER_EQUAL -> when {
      left !is Number -> runtimeError("Operand are not boolean", expr.left)
      right !is Number -> runtimeError("Operand are not boolean", expr.right)
      else -> left.toDouble() >= right.toDouble()
    }
    TokenType.LESS -> when {
      left !is Number -> runtimeError("Operand are not boolean", expr.left)
      right !is Number -> runtimeError("Operand are not boolean", expr.right)
      else -> left.toDouble() < right.toDouble()
    }
    TokenType.LESS_EQUAL -> when {
      left !is Number -> runtimeError("Operand are not boolean", expr.left)
      right !is Number -> runtimeError("Operand are not boolean", expr.right)
      else -> left.toDouble() <= right.toDouble()
    }
    TokenType.PLUS -> when {
      left !is Number -> runtimeError("Operand are not boolean", expr.left)
      right !is Number -> runtimeError("Operand are not boolean", expr.right)
      else -> left.toDouble() + right.toDouble()
    }
    TokenType.MINUS -> when {
      left !is Number -> runtimeError("Operand are not boolean", expr.left)
      right !is Number -> runtimeError("Operand are not boolean", expr.right)
      else -> left.toDouble() + right.toDouble()
    }
    TokenType.STAR -> when {
      left !is Number -> runtimeError("Operand are not boolean", expr.left)
      right !is Number -> runtimeError("Operand are not boolean", expr.right)
      else -> left.toDouble() * right.toDouble()
    }
    TokenType.SLASH -> when {
      left !is Number -> runtimeError("Operand are not boolean", expr.left)
      right !is Number -> runtimeError("Operand are not boolean", expr.right)
      else -> left.toDouble() / right.toDouble()
    }
    TokenType.PERCENT -> when {
      left !is Number -> runtimeError("Operand are not boolean", expr.left)
      right !is Number -> runtimeError("Operand are not boolean", expr.right)
      else -> left.toDouble() % right.toDouble()
    }
    else -> runtimeError("Unknown operator", expr.operator)
  }
}

private fun DB.resolveIn(row: Map<String, Any?>, expr: Expression.InOp): Boolean {
  val left = resolveExpression(row, expr.left)
  val collection = when (expr.right) {
    is InExpression.Tuple -> {
      expr.right.items.map { resolveExpression(row, it) }
    }
    is InExpression.Select -> {
      select(expr.right.query).rows.map { it.first() }
    }
  }
  return collection.contains(left)
}

private fun patternToRegex(pattern: String): Regex {
  return buildString {
    append("^")
    var start = 0
    for ((i, c) in pattern.withIndex()) {
      if ((c == '%' || c == '_') && pattern.getOrNull(i - 1) != '\\') {
        val chunk = pattern.substring(start, i)
        if (chunk.isNotEmpty()) {
          append(Regex.escape(chunk))
        }
        append(if (c == '%') "(.*)" else "(.)")
        start = i + 1
      }
    }
    val chunk = pattern.substring(start, pattern.length)
    if (chunk.isNotEmpty()) {
      append(Regex.escape(chunk))
    }
    append("$")
  }.toRegex()
}

private typealias Function = (name: Token, List<Any?>) -> Any?

private val functions: Map<String, Function> = mapOf(
  "lower" to { name, args ->
    val arg = args.singleOrNull()?.toString() ?: runtimeError("incorrect arguments to '${name.lexeme}'", name)
    arg.lowercase()
  },
  "upper" to { name, args ->
    val arg = args.singleOrNull()?.toString() ?: runtimeError("incorrect arguments to '${name.lexeme}'", name)
    arg.uppercase()
  }
)

private fun DB.resolveFunction(row: Row, call: Expression.FunctionCall): Any? {
  val nameToken = when (val e = call.expression) {
    is Expression.Variable -> e.name
    else -> runtimeError("invalid function call", e)
  }
  val f = functions[nameToken.lexeme] ?: runtimeError("Unknow function '${nameToken.lexeme}'", nameToken)
  val args = call.parameters.map { resolveExpression(row, it) }
  return f(nameToken, args)
}

private fun normalize(value: Any?): Any? {
  return when (value) {
    is Byte, is Short, is Int, is Long -> (value as Number).toLong()
    is Float, is Double -> (value as Number).toDouble()
    else -> value
  }
}

private fun Any?.truthy(): Boolean {
  return when (this) {
    is Boolean -> this
    is Number -> this != 0
    else -> false
  }
}

private fun cartesianProduct(tables: List<List<Row>>): List<Row> {
  return tables.fold(listOf(emptyMap())) { acc, table ->
    acc.flatMap { list ->
      table.map { element -> list + element }
    }
  }
}

private fun commonPrefix(strings: List<String>): String {
  if (strings.isEmpty()) {
    return ""
  }
  if (strings.size == 1) {
    return strings[0].split(".").dropLast(1).joinToString(".") + "."
  }
  val byParts = strings.map { it.split(".").dropLast(1) }
  val prefixParts = mutableListOf<String>()
  var i = 0
  while (byParts.all { it.getOrNull(i) == byParts[0].getOrNull(i) }) {
    prefixParts.add(byParts[0].getOrNull(i) ?: break)
    i++
  }
  return prefixParts.joinToString(".") + "."
}

private fun runtimeError(msg: String, token: Token): Nothing {
  throw RqlError(token.startOffset, token.endOffset, "Evaluation error $msg")
}

private fun runtimeError(msg: String, expr: Expression): Nothing {
  val location = expr.location()
  throw RqlError(location.first, location.second, "Evaluation error $msg")
}

private fun Expression.location(): Pair<Int, Int> {
  return when (this) {
    is Expression.Literal -> value.startOffset to value.endOffset
    is Expression.Variable -> name.startOffset to name.endOffset
    is Expression.UnaryOp -> operator.startOffset to operand.location().second
    is Expression.BinaryOp -> left.location().first to right.location().second
    is Expression.InOp -> left.location() // TODO: make location include InExpression
    is Expression.FunctionCall -> expression.location() // TODO: make location include params
  }
}

fun QueryResult.pretty(): String {
  return buildString {
    val size = maxOf(columnLabels.sorted().maxOf { it.length }, 15)
    val header = columnLabels.joinToString(" | ") { it.padEnd(size, ' ') }
    appendLine("")
    appendLine("-".repeat(header.length))
    appendLine(header)
    appendLine("=".repeat(header.length))
    for (row in rows) {
      appendLine(row.joinToString(" | ") { it.toString().take(size).padEnd(size, ' ') })
      appendLine("-".repeat(header.length))
    }
  }
}