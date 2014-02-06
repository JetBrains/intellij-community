/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util.importProject;

import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.JavaModuleSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.util.Consumer;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JavaModuleInsight extends ModuleInsight {
  private final Lexer myLexer;

  public JavaModuleInsight(@Nullable final ProgressIndicator progress,
                           Set<String> existingModuleNames,
                           Set<String> existingProjectLibraryNames) {
    super(progress, existingModuleNames, existingProjectLibraryNames);
    myLexer = JavaParserDefinition.createLexer(LanguageLevel.JDK_1_5);
  }

  @Override
  protected boolean isSourceFile(final File file) {
    return StringUtil.endsWithIgnoreCase(file.getName(), ".java");
  }

  @Override
  protected boolean isLibraryFile(final String fileName) {
    return StringUtil.endsWithIgnoreCase(fileName, ".jar") || StringUtil.endsWithIgnoreCase(fileName, ".zip");
  }

  @Override
  protected void scanSourceFileForImportedPackages(final CharSequence chars, final Consumer<String> result) {
    myLexer.start(chars);

    JavaSourceRootDetectionUtil.skipWhiteSpaceAndComments(myLexer);
    if (myLexer.getTokenType() == JavaTokenType.PACKAGE_KEYWORD) {
      advanceLexer(myLexer);
      if (readPackageName(chars, myLexer) == null) {
        return;
      }
    }

    while (true) {
      if (myLexer.getTokenType() == JavaTokenType.SEMICOLON) {
        advanceLexer(myLexer);
      }
      if (myLexer.getTokenType() != JavaTokenType.IMPORT_KEYWORD) {
        return;
      }
      advanceLexer(myLexer);

      boolean isStaticImport = false;
      if (myLexer.getTokenType() == JavaTokenType.STATIC_KEYWORD) {
        isStaticImport = true;
        advanceLexer(myLexer);
      }

      final String packageName = readPackageName(chars, myLexer);
      if (packageName == null) {
        return;
      }

      if (packageName.endsWith(".*")) {
        result.consume(packageName.substring(0, packageName.length() - ".*".length()));
      }
      else {
        int lastDot = packageName.lastIndexOf('.');
        if (lastDot > 0) {
          String _packageName = packageName.substring(0, lastDot);
          if (isStaticImport) {
            lastDot = _packageName.lastIndexOf('.');
            if (lastDot > 0) {
              result.consume(_packageName.substring(0, lastDot));
            }
          }
          else {
            result.consume(_packageName);
          }
        }
      }
    }
  }

  @Nullable
  private static String readPackageName(final CharSequence text, final Lexer lexer) {
    final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      while (true) {
        if (lexer.getTokenType() != JavaTokenType.IDENTIFIER && lexer.getTokenType() != JavaTokenType.ASTERISK) {
          break;
        }
        buffer.append(text, lexer.getTokenStart(), lexer.getTokenEnd());

        advanceLexer(lexer);
        if (lexer.getTokenType() != JavaTokenType.DOT) {
          break;
        }
        buffer.append('.');

        advanceLexer(lexer);
      }

      String packageName = buffer.toString();
      if (packageName.length() == 0 || StringUtil.endsWithChar(packageName, '.') || StringUtil.startsWithChar(packageName, '*')) {
        return null;
      }
      return packageName;
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }

  private static void advanceLexer(final Lexer lexer) {
    lexer.advance();
    JavaSourceRootDetectionUtil.skipWhiteSpaceAndComments(lexer);
  }

  @Override
  protected void scanLibraryForDeclaredPackages(File file, Consumer<String> result) throws IOException {
    final ZipFile zip = new ZipFile(file);
    try {
      final Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        final String entryName = entries.nextElement().getName();
        if (StringUtil.endsWithIgnoreCase(entryName, ".class")) {
          final int index = entryName.lastIndexOf('/');
          if (index > 0) {
            final String packageName = entryName.substring(0, index).replace('/', '.');
            result.consume(packageName);
          }
        }
      }
    }
    finally {
      zip.close();
    }
  }

  protected ModuleDescriptor createModuleDescriptor(final File moduleContentRoot, final Collection<DetectedSourceRoot> sourceRoots) {
    return new ModuleDescriptor(moduleContentRoot, StdModuleTypes.JAVA, sourceRoots);
  }

  public boolean isApplicableRoot(final DetectedProjectRoot root) {
    return root instanceof JavaModuleSourceRoot;
  }
}
