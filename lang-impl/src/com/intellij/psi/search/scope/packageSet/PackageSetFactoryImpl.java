package com.intellij.psi.search.scope.packageSet;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.search.scope.packageSet.lexer.ScopeTokenTypes;
import com.intellij.psi.search.scope.packageSet.lexer.ScopesLexer;
import org.jetbrains.annotations.Nullable;

public class PackageSetFactoryImpl extends PackageSetFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.search.scope.packageSet.PackageSetFactoryImpl");

  public PackageSet compile(String text) throws ParsingException {
    Lexer lexer = new ScopesLexer();
    lexer.start(text,0,text.length(),0);
    return new Parser(lexer).parse();
  }

  private static class Parser {
    private Lexer myLexer;

    public Parser(Lexer lexer) {
      myLexer = lexer;
    }

    public PackageSet parse() throws ParsingException {
      PackageSet set = parseUnion();
      if (myLexer.getTokenType() != null) error(AnalysisScopeBundle.message("error.packageset.token.expectations", getTokenText()));
      return set;
    }

    private PackageSet parseUnion() throws ParsingException {
      PackageSet result = parseIntersection();
      while (true) {
        if (myLexer.getTokenType() != ScopeTokenTypes.OROR) break;
        myLexer.advance();
        result = new UnionPackageSet(result, parseIntersection());
      }
      return result;
    }

    private PackageSet parseIntersection() throws ParsingException {
      PackageSet result = parseTerm();
      while (true) {
        if (myLexer.getTokenType() != ScopeTokenTypes.ANDAND) break;
        myLexer.advance();
        result = new IntersectionPackageSet(result, parseTerm());
      }
      return result;
    }

    private PackageSet parseTerm() throws ParsingException {
      if (myLexer.getTokenType() == ScopeTokenTypes.EXCL) {
        myLexer.advance();
        return new ComplementPackageSet(parseTerm());
      }

      if (myLexer.getTokenType() == ScopeTokenTypes.LPARENTH) return parseParenthesized();
      if (myLexer.getTokenType() == ScopeTokenTypes.IDENTIFIER && myLexer.getBufferSequence().charAt(myLexer.getTokenStart()) == '$') {
        NamedPackageSetReference namedPackageSetReference = new NamedPackageSetReference(getTokenText());
        myLexer.advance();
        return namedPackageSetReference;
      }
      return parsePattern();
    }

    private PackageSet parsePattern() throws ParsingException {
      String scope = null;
      for (PackageSetParserExtension extension : Extensions.getExtensions(PackageSetParserExtension.EP_NAME)) {
        scope = extension.parseScope(myLexer);
        if (scope != null) break;
      }
      if (scope == null) error("Unknown scope type");
      String modulePattern = parseModulePattern();

      if (myLexer.getTokenType() == ScopeTokenTypes.COLON) {
        myLexer.advance();
      }
      for (PackageSetParserExtension extension : Extensions.getExtensions(PackageSetParserExtension.EP_NAME)) {
        final PackageSet packageSet = extension.parsePackageSet(myLexer, scope, modulePattern);
        if (packageSet != null) return packageSet;
      }
      error("Unknown scope type");
      return null; //not reachable
    }

    private String getTokenText() {
      int start = myLexer.getTokenStart();
      int end = myLexer.getTokenEnd();
      return myLexer.getBufferSequence().subSequence(start, end).toString();
    }

    @Nullable
    private String parseModulePattern() throws ParsingException {
      if (myLexer.getTokenType() != ScopeTokenTypes.LBRACKET) return null;
      myLexer.advance();
      StringBuffer pattern = new StringBuffer();
      while (true) {
        if (myLexer.getTokenType() == ScopeTokenTypes.RBRACKET ||
            myLexer.getTokenType() == null) {
          myLexer.advance();
          break;
        } else if (myLexer.getTokenType() == ScopeTokenTypes.ASTERISK) {
          pattern.append("*");
        } else if (myLexer.getTokenType() == ScopeTokenTypes.IDENTIFIER ||
                   myLexer.getTokenType() == ScopeTokenTypes.WHITE_SPACE ||
                   myLexer.getTokenType() == ScopeTokenTypes.INTEGER_LITERAL ) {
          pattern.append(getTokenText());
        } else if (myLexer.getTokenType() == ScopeTokenTypes.DOT) {
          pattern.append(".");
        } else if (myLexer.getTokenType() == ScopeTokenTypes.MINUS) {
          pattern.append("-");
        } else if (myLexer.getTokenType() == ScopeTokenTypes.COLON) {
          pattern.append(":");
        } else {
          error(AnalysisScopeBundle.message("error.packageset.token.expectations", getTokenText()));
          break;
        }
        myLexer.advance();
      }
      if (pattern.length() == 0) {
        error(AnalysisScopeBundle.message("error.packageset.pattern.expectations"));
      }
      return pattern.toString();
    }

    private PackageSet parseParenthesized() throws ParsingException {
      LOG.assertTrue(myLexer.getTokenType() == ScopeTokenTypes.LPARENTH);
      myLexer.advance();

      PackageSet result = parseUnion();
      if (myLexer.getTokenType() != ScopeTokenTypes.RPARENTH) error(AnalysisScopeBundle.message("error.packageset.rparen.expected"));
      myLexer.advance();

      return result;
    }

    private void error(String message) throws ParsingException {
      throw new ParsingException(
        AnalysisScopeBundle.message("error.packageset.position.parsing.error", message, (myLexer.getTokenStart() + 1)));
    }
  }
}