// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb.rql

/* --------- Grammar:

query             -> SELECT columnSelection (',' columnSelection)*
                        from?
                        where?
                        groupBy?
                        orderBy?
                        limit?
columnSelection   -> '*' | expression
expression        -> primary
                    | '(' expression ')'
                    | expression '.' expression
                    | expression '(' parameters ')'
                    | ('+' | '-' | 'NOT') expression
                    | expression ('*' | '/' | '%') expression
                    | expression ('+' | '-') expression
                    | expression ('>' | '>=' | '<' | '<=') expression
                    | expression 'IN' STRING
                    | expression ('=' | '!=') expression
                    | expression 'AND' expression
                    | expression 'OR' expression
                    | expression 'IN' inExpression
primary           -> INTEGER | DOUBLE | FLOAT | STRING | IDENTIFIER
listing           -> expression (',' expression)*
inExpresion       -> query
                    | '(' query ')'
                    | '(' listing ')'
from              -> FROM fromSource (',' fromSource)*
fromSource        -> IDENTIFIER
                    | fromSource JOIN IDENTIFIER ON expression
where             -> WHERE expression
groupBy           -> GROUP BY listing (HAVING expression)?
orderBy           -> ORDER BY orderTerm (, orderTerm)*
orderTerm         -> expression (ASC | DESC)?
limit             -> LIMIT expression (OFFSET expression)?
 */

class Parser(source: String) {
  private val lexer = Lexer(source)
  private var current = lexer.scanToken()

  fun parse(): Query {
    val query = query()
    expect(TokenType.EOF)
    return query
  }

  private fun query(): Query {
    return Query(
      select = columnSelection(),
      from = from(),
      where = where(),
      groupBy = groupBy(),
      orderBy = orderBy(),
      limit = limit()
    )
  }

  private fun columnSelection(): ColumnSelection {
    expect(TokenType.SELECT, "Expected SELECT")
    return when (peek()) {
      TokenType.STAR -> {
        advance()
        ColumnSelection.Wildcard
      }
      else -> {
        val columns = mutableListOf(column())
        while (match(TokenType.COMMA)) {
          columns.add(column())
        }
        ColumnSelection.Columns(columns)
      }
    }
  }

  private fun column(): Column {
    val e = expression()
    val alias = if (match(TokenType.AS)) {
      expect(TokenType.IDENTIFIER, "Alias should be an identifier").lexeme
    }
    else {
      null
    }
    return Column(e, alias)
  }

  private fun from(): List<FromSource> {
    if (match(TokenType.FROM)) {
      val sources = mutableListOf(fromSource())
      while (match(TokenType.COMMA)) {
        sources.add(fromSource())
      }
      return sources
    }
    return emptyList()
  }

  private fun fromSource(): FromSource {
    val first = expect(TokenType.IDENTIFIER, "Expected identifier")
    var result: FromSource = FromSource.Table(first)
    while (match(TokenType.JOIN)) {
      val next = expect(TokenType.IDENTIFIER, "Expected identifier after JOIN")
      expect(TokenType.ON, "Expected ON after JOIN")
      val on = expression()
      result = FromSource.Join(result, next, on)
    }
    return result
  }

  private fun where(): Expression? {
    if (match(TokenType.WHERE)) {
      return expression()
    }
    return null
  }

  private fun groupBy(): GroupBy? {
    if (match(TokenType.GROUP)) {
      expect(TokenType.BY, "Expected GROUP BY")
      val expressions = listing()
      val having = if (match(TokenType.HAVING)) expression() else null
      return GroupBy(expressions, having)
    }
    return null
  }

  private fun orderBy(): OrderBy? {
    if (match(TokenType.ORDER)) {
      expect(TokenType.BY, "Expected ORDER BY")
      val terms = mutableListOf(orderTerm())
      while (match(TokenType.COMMA)) {
        terms.add(orderTerm())
      }
      return OrderBy(terms)
    }
    return null
  }

  private fun limit(): Limit? {
    if (match(TokenType.LIMIT)) {
      val limit = expression()
      val offset = if (match(TokenType.OFFSET)) {
        expression()
      }
      else null
      return Limit(limit, offset)
    }
    return null
  }

  private fun orderTerm(): OrderTerm {
    val e = expression()
    val ascending = match(TokenType.ASC) || !match(TokenType.DESC)
    return OrderTerm(e, ascending)
  }

  private fun expression(): Expression {
    return parsePrecedence(0)
  }

  private fun primary(): Expression {
    return when (peek()) {
      TokenType.INTEGER,
        TokenType.FLOAT,
        TokenType.DOUBLE,
        TokenType.STRING,
        TokenType.TRUE,
        TokenType.FALSE,
        TokenType.NULL -> Expression.Literal(advance())
      TokenType.IDENTIFIER -> Expression.Variable(advance())
      else -> parseError("Unexpected token '${current.lexeme}'")
    }
  }

  private fun listing(): List<Expression> {
    if (peek() == TokenType.RPAREN) return emptyList()
    val params = mutableListOf(expression())
    while (match(TokenType.COMMA)) {
      params.add(expression())
    }
    return params
  }

  private fun inExpression(): InExpression {
    if (peek() == TokenType.SELECT) {
      return InExpression.Select(query())
    }
    expect(TokenType.LPAREN, "Expecting ( after IN clause")
    if (peek() == TokenType.SELECT) {
      val q = query()
      expect(TokenType.RPAREN, "Expecting ) after query")
      return InExpression.Select(q)
    }
    val items = listing()
    expect(TokenType.RPAREN, "Expecting )")
    return InExpression.Tuple(items)
  }

  private fun parsePrecedence(minPrecedence: Int): Expression {
    val prefixPrecedence = when (peek()) {
      TokenType.PLUS, TokenType.MINUS, TokenType.NOT -> 8
      else -> null
    }
    var left = if (prefixPrecedence != null) {
      val operator = advance()
      val operand = parsePrecedence(prefixPrecedence)
      Expression.UnaryOp(operator, operand)
    }
    else if (match(TokenType.LPAREN)) {
      val inner = parsePrecedence(0)
      expect(TokenType.RPAREN, "Expected )")
      inner
    }
    else {
      primary()
    }
    while (true) {
      val infixPrecedence = when (peek()) {
        TokenType.OR -> 1
        TokenType.AND -> 2
        TokenType.EQUAL, TokenType.NOT_EQUAL, TokenType.LIKE, TokenType.IN -> 4
        TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL -> 5
        TokenType.PLUS, TokenType.MINUS -> 6
        TokenType.STAR, TokenType.SLASH, TokenType.PERCENT -> 7
        TokenType.LPAREN -> 9
        else -> break
      }
      if (infixPrecedence <= minPrecedence) break
      val operator = advance()
      when (operator.type) {
        TokenType.IN -> {
          val right = inExpression()
          left = Expression.InOp(left, right)
        }
        TokenType.LIKE -> {
          val pattern = expect(TokenType.STRING, "Expected string after LIKE")
          val right = Expression.Literal(pattern)
          left = Expression.BinaryOp(left, operator, right)
        }
        TokenType.LPAREN -> {
          val parameters = listing()
          expect(TokenType.RPAREN, "Expected )")
          left = Expression.FunctionCall(left, parameters)
        }
        else -> {
          val right = parsePrecedence(infixPrecedence)
          left = Expression.BinaryOp(left, operator, right)
        }
      }
    }
    return left
  }

  private fun peek() = current.type

  private fun match(tokenType: TokenType): Boolean {
    if (peek() == tokenType) {
      advance()
      return true
    }
    return false
  }

  private fun advance(): Token {
    val token = current
    current = lexer.scanToken()
    return token
  }

  private fun expect(expected: TokenType, message: String? = null): Token {
    if (peek() != expected) {
      parseError(message ?: "Unexpected token '${current.lexeme}'")
    }
    return advance()
  }

  private fun parseError(msg: String, token: Token = current): Nothing {
    throw RqlError(token.startOffset, token.endOffset, "Parse error: $msg")
  }
}

class RqlError(val startOffset: Int, val endOffset: Int, val msg: String):
  Exception("[$startOffset, $endOffset] $msg")