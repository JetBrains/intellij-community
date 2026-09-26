// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb.rql

data class Query(val select: ColumnSelection,
                 val from: List<FromSource>,
                 val where: Expression?,
                 val groupBy: GroupBy?,
                 val orderBy: OrderBy?,
                 val limit: Limit?)

sealed class ColumnSelection {
  object Wildcard : ColumnSelection()
  data class Columns(val columns: List<Column>): ColumnSelection()
}

data class Column(val expression: Expression, val alias: String?)

sealed class Expression {
  data class Literal(val value: Token) : Expression()
  data class Variable(val name: Token) : Expression()
  data class UnaryOp(val operator: Token, val operand: Expression) : Expression()
  data class BinaryOp(val left: Expression, val operator: Token, val right: Expression) : Expression()
  data class InOp(val left: Expression, val right: InExpression): Expression()
  data class FunctionCall(val expression: Expression, val parameters: List<Expression>): Expression()
}

sealed class InExpression {
  data class Tuple(val items: List<Expression>): InExpression()
  data class Select(val query: Query): InExpression()
}

sealed class FromSource {
  data class Table(val name: Token): FromSource()
  data class Join(val left: FromSource, val right: Token, val on: Expression): FromSource()
}

data class GroupBy(val expressions: List<Expression>, val having: Expression?)

data class OrderBy(val terms: List<OrderTerm>)

data class Limit(val limit: Expression, val offset: Expression?)

data class OrderTerm(val expression: Expression, val ascending: Boolean)
