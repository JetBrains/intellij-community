/*
 * User: anna
 * Date: 25-Jan-2008
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

public interface PackageSetParserExtension {
  ExtensionPointName<PackageSetParserExtension> EP_NAME = ExtensionPointName.create("com.intellij.scopeParserExtension");

  @Nullable
  PackageSet parsePackageSet(Lexer lexer, final String scope, String modulePattern) throws ParsingException;

  @Nullable
  String parseScope(Lexer lexer);
}