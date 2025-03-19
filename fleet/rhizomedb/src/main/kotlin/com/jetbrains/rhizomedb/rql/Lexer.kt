// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb.rql

enum class TokenType(val isKeyword: Boolean = true) {
  ERROR,
  EOF,

  MINUS,
  PLUS,
  STAR,
  SLASH,
  PERCENT,
  EQUAL,
  GREATER,
  LESS,
  GREATER_EQUAL,
  LESS_EQUAL,
  NOT_EQUAL,

  LPAREN,
  RPAREN,
  COMMA,

  INTEGER,
  DOUBLE,
  FLOAT,
  STRING,

  IDENTIFIER,

  TRUE(true),
  FALSE(true),
  NULL(true),
  SELECT(true),
  FROM(true),
  WHERE(true),
  GROUP(true),
  BY(true),
  HAVING(true),
  ORDER(true),
  DESC(true),
  ASC(true),
  LIMIT(true),
  OFFSET(true),
  JOIN(true),
  AS(true),
  ON(true),

  NOT(true),
  AND(true),
  OR(true),
  IN(true),
  LIKE(true),
}

private val KEYWORDS = TokenType.entries.filter(TokenType::isKeyword).associateBy { it.name.lowercase() }

data class Token(
  val type: TokenType,
  val lexeme: String,
  val startOffset: Int,
  val endOffset: Int,
  val literal: Any? = null
)

class Lexer(private val source: String) {
  private var lexemeStart = 0
  private var offset = 0

  fun scanToken(): Token {
    skipWhiteSpace()
    if (isAtEnd()) {
      return newToken(TokenType.EOF)
    }
    lexemeStart = offset
    return when (val char = advance()) {
      '(' -> newToken(TokenType.LPAREN)
      ')' -> newToken(TokenType.RPAREN)
      ',' -> newToken(TokenType.COMMA)
      '+' -> newToken(TokenType.PLUS)
      '-' -> newToken(TokenType.MINUS)
      '*' -> newToken(TokenType.STAR)
      '/' -> newToken(TokenType.SLASH)
      '%' -> newToken(TokenType.PERCENT)
      '=' -> newToken(TokenType.EQUAL)

      '>' -> if (peek() == '=') {
        advance()
        newToken(TokenType.GREATER_EQUAL)
      }
      else {
        newToken(TokenType.GREATER)
      }

      '<' -> when (peek()) {
        '=' -> {
          advance()
          newToken(TokenType.LESS_EQUAL)
        }

        '>' -> {
          advance()
          newToken(TokenType.NOT_EQUAL)
        }

        else -> newToken(TokenType.LESS)
      }

      '!' -> if (peek() == '=') {
        advance()
        newToken(TokenType.NOT_EQUAL)
      }
      else {
        error("Unexpected character '${peek()}'")
      }

      '\'' -> scanString()

      else -> when {
        char.isDigit() -> scanNumberLiteral()
        char.isLetter() || char == '_' || char == '.' -> scanIdentifierOrKeyword()
        else -> error("Unexpected character '${char}'")
      }
    }
  }

  private fun scanString(): Token {
    val literal = StringBuilder()
    var badEscape: Token? = null
    while (!isAtEnd() && peek() != '\'') {
      if (peek() == '\\') {
        when (val escape = peek(1)) {
          '\'' -> literal.append('\'')
          'n' -> literal.append('\n')
          else -> badEscape = error("Unknown string escape '\\$escape'")
        }
        advance()
        advance()
      }
      else {
        literal.append(peek())
        advance()
      }
    }
    if (isAtEnd()) {
      return error("Unterminated string")
    }
    advance()
    if (badEscape != null) {
      return badEscape
    }
    return newToken(TokenType.STRING, literal.toString())
  }

  private fun scanIdentifierOrKeyword(): Token {
    while (peek().isLetterOrDigit() || peek() == '_' || peek() == '.') advance()
    val tokenType = KEYWORDS[currentLexeme().lowercase()] ?: TokenType.IDENTIFIER
    val literal = when (tokenType) {
      TokenType.TRUE -> true
      TokenType.FALSE -> false
      else -> null
    }
    return newToken(tokenType, literal)
  }

  private fun skipWhiteSpace() {
    while (!isAtEnd()) {
      when {
        peek() == '-' && peek(1) == '-' -> skipLineComment()
        peek() == '/' && peek(1) == '*' -> skipBlockComment()
        peek().isWhitespace() -> advance()
        else -> break
      }
    }
  }

  private fun skipLineComment() {
    do advance() while (!isAtEnd() && peek() != '\n')
  }

  private fun skipBlockComment() {
    advance()
    advance()
    while (!isAtEnd() && !(peek() == '*' && peek(1) == '/')) {
      advance()
    }
    if (!isAtEnd()) advance()
    if (!isAtEnd()) advance()
  }

  private fun scanNumberLiteral(): Token {
    while (peek().isDigit()) {
      advance()
    }
    return if (peek() == '.') {
      advance()
      while (peek().isDigit()) {
        advance()
      }
      when (peek()) {
        'f', 'F' -> {
          advance()
          newToken(TokenType.FLOAT, currentLexeme().toFloat())
        }

        'd', 'D' -> {
          advance()
          newToken(TokenType.DOUBLE, currentLexeme().toDouble())
        }

        else -> {
          newToken(TokenType.DOUBLE, currentLexeme().toDouble())
        }
      }
    }
    else {
      newToken(TokenType.INTEGER, currentLexeme().toInt())
    }
  }

  private fun newToken(type: TokenType, literal: Any? = null) = Token(type, currentLexeme(), lexemeStart, offset, literal)
  private fun error(message: String) = newToken(TokenType.ERROR, message)

  private fun isAtEnd() = offset >= source.length
  private fun currentLexeme() = source.slice(lexemeStart until offset)
  private fun advance() = source[offset++]
  private fun peek(n: Int = 0): Char {
    val pos = offset + n
    return if (pos >= source.length) {
      '\u0000'
    }
    else {
      source[pos]
    }
  }
}
