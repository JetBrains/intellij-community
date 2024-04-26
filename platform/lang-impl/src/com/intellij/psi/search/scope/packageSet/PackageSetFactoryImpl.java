// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.search.scope.packageSet;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.icons.AllIcons;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.TokenType;
import com.intellij.psi.search.scope.packageSet.lexer.ScopeTokenTypes;
import com.intellij.psi.search.scope.packageSet.lexer.ScopesLexer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PackageSetFactoryImpl extends PackageSetFactory {
  private static final Logger LOG = Logger.getInstance(PackageSetFactoryImpl.class);

  public PackageSetFactoryImpl() {
    PackageSetParserExtension.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull PackageSetParserExtension extension, @NotNull PluginDescriptor pluginDescriptor) {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          for (NamedScopesHolder holder : NamedScopesHolder.getAllNamedScopeHolders(project)) {
            ServiceKt.getStateStore(project).reloadState(holder.getClass());
            holder.fireScopeListeners();
          }
        }
      }

      @Override
      public void extensionRemoved(@NotNull PackageSetParserExtension extension, @NotNull PluginDescriptor pluginDescriptor) {
        ClassLoader pluginClassLoader = pluginDescriptor.getClassLoader();
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          for (NamedScopesHolder holder : NamedScopesHolder.getAllNamedScopeHolders(project)) {
            boolean changed = false;
            NamedScope[] scopes = holder.getScopes();
            for (int i = 0; i < scopes.length; i++) {
              NamedScope scope = scopes[i];
              PackageSet value = scope.getValue();
              if (value != null && value.getClass().getClassLoader() == pluginClassLoader) {
                String presentableName = scope.getPresentableName();
                scopes[i] = new NamedScope(scope.getScopeId(), () -> presentableName, AllIcons.Ide.LocalScope,
                                           new InvalidPackageSet(value.getText()));
                changed = true;
              }
            }
            if (changed) {
              holder.setScopes(scopes);
            }
          }
        }
      }
    }, ApplicationManager.getApplication());
  }

  @Override
  public PackageSet compile(String text) throws ParsingException {
    Lexer lexer = new ScopesLexer();
    lexer.start(text);
    return new Parser(lexer).parse();
  }

  private static final class Parser {
    private final Lexer myLexer;

    Parser(Lexer lexer) {
      myLexer = lexer;
    }

    public PackageSet parse() throws ParsingException {
      PackageSet set = parseUnion();
      if (myLexer.getTokenType() != null) error(CodeInsightBundle.message("error.package.set.token.expectations", getTokenText()));
      return set;
    }

    private PackageSet parseUnion() throws ParsingException {
      List<PackageSet> sets = new ArrayList<>();
      PackageSet set = parseIntersection();
      sets.add(set);
      while (myLexer.getTokenType() == ScopeTokenTypes.OROR) {
        myLexer.advance();
        sets.add(parseIntersection());
      }
      return UnionPackageSet.create(sets.toArray(new PackageSet[0]));
    }

    private PackageSet parseIntersection() throws ParsingException {
      PackageSet set = parseTerm();
      List<PackageSet> sets = new ArrayList<>();
      sets.add(set);
      while (myLexer.getTokenType() == ScopeTokenTypes.ANDAND) {
        myLexer.advance();
        sets.add(parseTerm());
      }
      return IntersectionPackageSet.create(sets.toArray(new PackageSet[0]));
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
      PackageSetParserExtension usedExtension = null;
      for (PackageSetParserExtension extension : PackageSetParserExtension.EP_NAME.getExtensionList()) {
        scope = extension.parseScope(myLexer);
        if (scope != null) {
          usedExtension = extension;
          break;
        }
      }
      if (scope == null) error("Unknown scope type");
      String modulePattern = parseModulePattern();

      if (myLexer.getTokenType() == ScopeTokenTypes.COLON) {
        myLexer.advance();
      }
      final PackageSet packageSet = usedExtension.parsePackageSet(myLexer, scope, modulePattern);
      if (packageSet != null) return packageSet;
      error("Unknown scope type");
      return null; //not reachable
    }

    private String getTokenText() {
      int start = myLexer.getTokenStart();
      int end = myLexer.getTokenEnd();
      return myLexer.getBufferSequence().subSequence(start, end).toString();
    }

    private @Nullable String parseModulePattern() throws ParsingException {
      if (myLexer.getTokenType() != ScopeTokenTypes.LBRACKET) return null;
      myLexer.advance();
      StringBuilder pattern = new StringBuilder();
      while (true) {
        if (myLexer.getTokenType() == ScopeTokenTypes.RBRACKET ||
            myLexer.getTokenType() == null) {
          myLexer.advance();
          break;
        } else if (myLexer.getTokenType() == ScopeTokenTypes.ASTERISK) {
          pattern.append("*");
        } else if (myLexer.getTokenType() == ScopeTokenTypes.IDENTIFIER ||
                   myLexer.getTokenType() == TokenType.WHITE_SPACE ||
                   myLexer.getTokenType() == ScopeTokenTypes.INTEGER_LITERAL ) {
          pattern.append(getTokenText());
        } else if (myLexer.getTokenType() == ScopeTokenTypes.DOT) {
          pattern.append(".");
        } else if (myLexer.getTokenType() == ScopeTokenTypes.MINUS) {
          pattern.append("-");
        } else if (myLexer.getTokenType() == ScopeTokenTypes.TILDE) {
          pattern.append("~");
        } else if (myLexer.getTokenType() == ScopeTokenTypes.SHARP) {
          pattern.append("#");
        }
        else if (myLexer.getTokenType() == ScopeTokenTypes.COLON) {
          pattern.append(":");
        } else {
          pattern.append(getTokenText());
        }
        myLexer.advance();
      }
      if (pattern.isEmpty()) {
        error(CodeInsightBundle.message("error.package.set.pattern.expectations"));
      }
      return pattern.toString();
    }

    private PackageSet parseParenthesized() throws ParsingException {
      LOG.assertTrue(myLexer.getTokenType() == ScopeTokenTypes.LPARENTH);
      myLexer.advance();

      PackageSet result = parseUnion();
      if (myLexer.getTokenType() != ScopeTokenTypes.RPARENTH) error(CodeInsightBundle.message("error.package.set.rparen.expected"));
      myLexer.advance();

      return result;
    }

    private void error(@NotNull String message) throws ParsingException {
      throw new ParsingException(
        CodeInsightBundle.message("error.package.set.position.parsing.error", message, (myLexer.getTokenStart() + 1)));
    }
  }
}
