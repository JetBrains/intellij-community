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
package com.intellij.ide.util.projectWizard.importSources;

import com.intellij.ide.util.importProject.RootDetectionProcessor;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JavaSourceRootDetectionUtil {
  private static final TokenSet JAVA_FILE_FIRST_TOKEN_SET = TokenSet.orSet(
    ElementType.MODIFIER_BIT_SET,
    ElementType.CLASS_KEYWORD_BIT_SET,
    TokenSet.create(JavaTokenType.AT, JavaTokenType.IMPORT_KEYWORD)
  );

  private JavaSourceRootDetectionUtil() { }

  @NotNull
  public static Collection<JavaModuleSourceRoot> suggestRoots(@NotNull File dir) {
    final List<JavaSourceRootDetector> detectors = ContainerUtil.findAll(ProjectStructureDetector.EP_NAME.getExtensions(), JavaSourceRootDetector.class);
    final RootDetectionProcessor processor = new RootDetectionProcessor(dir, detectors.toArray(new JavaSourceRootDetector[detectors.size()]));
    final Map<ProjectStructureDetector,List<DetectedProjectRoot>> rootsMap = processor.runDetectors();

    Map<File, JavaModuleSourceRoot> result = new HashMap<>();
    for (List<DetectedProjectRoot> roots : rootsMap.values()) {
      for (DetectedProjectRoot root : roots) {
        if (root instanceof JavaModuleSourceRoot) {
          final JavaModuleSourceRoot sourceRoot = (JavaModuleSourceRoot)root;
          final File directory = sourceRoot.getDirectory();
          final JavaModuleSourceRoot oldRoot = result.remove(directory);
          if (oldRoot != null) {
            result.put(directory, oldRoot.combineWith(sourceRoot));
          }
          else {
            result.put(directory, sourceRoot);
          }
        }
      }
    }
    return result.values();
  }

  @Nullable
  public static String getPackageName(CharSequence text) {
    Lexer lexer = JavaParserDefinition.createLexer(LanguageLevel.JDK_1_5);
    lexer.start(text);
    skipWhiteSpaceAndComments(lexer);
    skipAnnotations(lexer);
    final IElementType firstToken = lexer.getTokenType();
    if (firstToken != JavaTokenType.PACKAGE_KEYWORD) {
      if (JAVA_FILE_FIRST_TOKEN_SET.contains(firstToken)) {
        return "";
      }
      return null;
    }
    lexer.advance();
    skipWhiteSpaceAndComments(lexer);

    final StringBuilder buffer = new StringBuilder();
    while(true){
      if (lexer.getTokenType() != JavaTokenType.IDENTIFIER) break;
      buffer.append(text, lexer.getTokenStart(), lexer.getTokenEnd());
      lexer.advance();
      skipWhiteSpaceAndComments(lexer);
      if (lexer.getTokenType() != JavaTokenType.DOT) break;
      buffer.append('.');
      lexer.advance();
      skipWhiteSpaceAndComments(lexer);
    }
    String packageName = buffer.toString();
    if (packageName.length() == 0 || StringUtil.endsWithChar(packageName, '.')) return null;
    return packageName;
  }

  public static void skipWhiteSpaceAndComments(Lexer lexer){
    while(ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(lexer.getTokenType())) {
      lexer.advance();
    }
  }

  private static void skipAnnotations(Lexer lexer){
    while (lexer.getTokenType() == JavaTokenType.AT) {
      lexer.advance();
      skipQualifiedIdentifier(lexer);
      skipArguments(lexer);
      skipWhiteSpaceAndComments(lexer);
    }
  }

  private static void skipQualifiedIdentifier(Lexer lexer) {
    if (lexer.getTokenType() == JavaTokenType.IDENTIFIER) {
      lexer.advance();
      skipWhiteSpaceAndComments(lexer);
      while (lexer.getTokenType() == JavaTokenType.DOT) {
        lexer.advance();
        skipWhiteSpaceAndComments(lexer);
        if (lexer.getTokenType() != JavaTokenType.IDENTIFIER) {
          break;
        }
        lexer.advance();
        skipWhiteSpaceAndComments(lexer);
      }
      skipWhiteSpaceAndComments(lexer);
    }
  }

  private static void skipArguments(Lexer lexer) {
    skipWhiteSpaceAndComments(lexer);
    if (lexer.getTokenType() == JavaTokenType.LPARENTH) {
      lexer.advance();
      int depth = 1;
      while (depth > 0) {
        IElementType tokenType = lexer.getTokenType();
        if (tokenType == JavaTokenType.LPARENTH) {
          depth++;
        }
        else if (tokenType == JavaTokenType.RPARENTH) {
          depth--;
        }
        else {
          if (tokenType == null) {
            break;
          }
        }
        lexer.advance();
      }
      skipWhiteSpaceAndComments(lexer);
    }
  }
}
