// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import com.intellij.lang.java.lexer.BasicJavaLexer;
import com.intellij.lang.java.parser.BasicJavaParser;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.impl.source.AbstractBasicJavaDocElementTypeFactory;
import com.intellij.psi.impl.source.AbstractBasicJavaElementTypeFactory;
import com.intellij.psi.impl.source.BasicJavaDocElementType;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementTypeFactory;
import com.intellij.psi.impl.source.tree.JavaElementTypeFactory;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentProviderElementType;
import com.intellij.util.containers.ContainerUtil;
import org.junit.Assert;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class FrontBackElementTypeTest extends AbstractBasicJavaParsingTestCase {
  public FrontBackElementTypeTest() {
    super("dummy", new JavaParsingTestConfigurator());
  }

  public void testJavaElementType() {
    AbstractBasicJavaElementTypeFactory.JavaElementTypeContainer javaElementTypeContainer = JavaElementTypeFactory.INSTANCE.getContainer();
    check(javaElementTypeContainer);
  }

  public void testJavaDocElementType() {
    AbstractBasicJavaDocElementTypeFactory.JavaDocElementTypeContainer javaDocElementTypeContainer =
      JavaDocElementTypeFactory.INSTANCE.getContainer();
    check(javaDocElementTypeContainer);
  }

  public void testFrontEndElementTypesDontUsedInParserDirectly() throws IOException {
    //in different folders
    Class lexer = BasicJavaLexer.class;
    String lexerPath = lexer.getName();
    lexerPath = lexerPath.substring(0, lexerPath.length() - lexer.getSimpleName().length());

    Class parser = BasicJavaParser.class;
    String parserPath = parser.getName();
    parserPath = parserPath.substring(0, parserPath.length() - parser.getSimpleName().length());

    Class backJavaElementType = BasicJavaElementType.class;
    String backJavaElementTypePath = backJavaElementType.getName();
    backJavaElementTypePath =
      backJavaElementTypePath.substring(0, backJavaElementTypePath.length() - backJavaElementType.getSimpleName().length());

    Assert.assertNotEquals(lexerPath, backJavaElementTypePath, "Lexer and element types must be placed in different folders");
    Assert.assertNotEquals(parserPath, backJavaElementTypePath, "Lexer and element types must be placed in different folders");

    //not used in parser and lexer
    System.out.println();
    String pathToModule = FileUtil.toSystemDependentName("/../../java-frontback-psi-impl/src/");
    String fullPath = PathManagerEx.getTestDataPath() + pathToModule;
    checkContains(fullPath + FileUtil.toSystemDependentName(lexerPath.replace(".", "/")), BasicJavaElementType.class.getSimpleName());
    checkContains(fullPath + FileUtil.toSystemDependentName(lexerPath.replace(".", "/")), BasicJavaDocElementType.class.getSimpleName());
    checkContains(fullPath + FileUtil.toSystemDependentName(parserPath.replace(".", "/")), BasicJavaElementType.class.getSimpleName());
    checkContains(fullPath + FileUtil.toSystemDependentName(parserPath.replace(".", "/")), BasicJavaDocElementType.class.getSimpleName());
  }

  private static void checkContains(String pathToCheck, String target) throws IOException {
    List<Path> paths = Files.walk(Paths.get(pathToCheck).normalize())
      .filter(Files::isRegularFile)
      .toList();
    for (Path path : paths) {
      //exclude, because element types can be used there
      if (path.toString().endsWith("BasicJavaParserUtil.java")) {
        continue;
      }
      String content = FileUtil.loadFile(path.toFile());
      int start = 0;
      int find = content.indexOf(target, start);
      while (find != -1) {
        if (find == 0) {
          Assert.fail("Probably, file: " + path + " contains reference to " + target);
        }
        if (!StringUtil.isLatinAlphanumeric(String.valueOf(content.charAt(find - 1)))) {
          Assert.fail("Probably, file: " + path + " contains reference to " + target);
        }
        find = content.indexOf(target, find + 1);
      }
    }
  }

  private static void check(Object container) {
    Class<?> aClass = container.getClass();
    Field[] fields = aClass.getDeclaredFields();
    for (Field field : fields) {
      field.setAccessible(true);
      try {
        Object o = field.get(container);
        if (!(o instanceof IElementType elementType)) {
          Assert.fail("Object: " + o + " is not IElementType");
          return;
        }
        if (!(elementType instanceof ParentProviderElementType parentProviderElementType)) {
          Assert.fail(elementType + " must inherit ParentProviderElementType to use in Frontend part");
          return;
        }
        if (!ContainerUtil.or(parentProviderElementType.getParents(),
                              parent -> parent.getDebugName().equals(elementType.getDebugName()) &&
                                        parent.getLanguage().equals(elementType.getLanguage()) &&
                                        elementType.isLeftBound() == parent.isLeftBound())) {
          Assert.fail(elementType + " must contain parents, which can replace it in Frontend part. Found: " +
                      Strings.join(parentProviderElementType.getParents(), ", "));
        }
      }
      catch (IllegalAccessException e) {
        Assert.fail(e.getMessage());
      }
    }
  }
}
