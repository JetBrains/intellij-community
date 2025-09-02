// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import com.intellij.lang.java.lexer.BasicJavaLexer;
import com.intellij.lang.java.parser.BasicJavaParser;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementTypeFactory;
import com.intellij.psi.impl.source.tree.JavaElementTypeFactory;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentAwareTokenSet;
import com.intellij.psi.tree.ParentProviderElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import org.assertj.core.api.Assertions;
import org.junit.Assert;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class FrontBackElementTypeTest extends AbstractBasicJavaParsingTestCase {

  public static final String BASIC = "BASIC_";

  public FrontBackElementTypeTest() {
    super("dummy", new JavaParsingTestConfigurator());
  }

  public void testJavaTokenSet() throws IOException, IllegalAccessException {
    checkTokenSetContains(ElementType.class, BasicJavaElementType.class);
    checkTokenSet(ElementType.class, BasicElementTypes.class);
  }

  public void testJavaDocTokenSet() throws IllegalAccessException {
    checkTokenSet(JavaDocElementType.class, BasicJavaDocElementType.class);
  }

  private static void checkTokenSet(Class<?> mainTokenSetClass, Class<?> basicTokenSetClass)
    throws IllegalAccessException {
    Map<String, TokenSet> mainTokenSets = getFieldsByName(mainTokenSetClass, TokenSet.class);
    Map<String, ParentAwareTokenSet> mainParentTokenSets = getFieldsByName(mainTokenSetClass, ParentAwareTokenSet.class);

    Assert.assertEquals("ParentAwareTokenSet should not be used in " + mainTokenSetClass.getName() + ", use TokenSet",
                        0, mainParentTokenSets.size());

    Map<String, TokenSet> basicTokenSets = getFieldsByName(basicTokenSetClass, TokenSet.class);
    Map<String, ParentAwareTokenSet> basicParentTokenSets = getFieldsByName(basicTokenSetClass, ParentAwareTokenSet.class);

    IElementType[] allLoadedTypes = IElementType.enumerate(e -> true);
    for (Map.Entry<String, TokenSet> basicTokenSet : basicTokenSets.entrySet()) {
      ParentAwareTokenSet parentAwareTokenSet = ParentAwareTokenSet.create(basicTokenSet.getValue());
      Set<IElementType> parentContained = getContained(parentAwareTokenSet, allLoadedTypes);
      Set<IElementType> contained = getContained(basicTokenSet.getValue(), allLoadedTypes);
      Assertions.assertThat(parentContained)
        .withFailMessage("TokenSet " +
                         basicTokenSet.getKey() +
                         " probably contains ParentProviderElementType and should be converted to ParentAwareTokenSet")
        .containsExactlyInAnyOrderElementsOf(contained);
    }

    for (Map.Entry<String, TokenSet> basicTokenSetsEntry : basicTokenSets.entrySet()) {
      String basicTokenSetName = basicTokenSetsEntry.getKey();
      if (!basicTokenSetName.startsWith(BASIC)) {
        Assertions.fail("Name of TokenSet " + basicTokenSetName + " doesn't start with " + BASIC + " in " + basicTokenSetClass.getName());
      }
      String expectedBasicTokenSetName = basicTokenSetName.substring(BASIC.length());
      TokenSet tokenSet = mainTokenSets.get(expectedBasicTokenSetName);
      if (tokenSet == null) {
        Assertions.fail(mainTokenSetClass.getName() + " doesn't contain TokenSet with name " + expectedBasicTokenSetName + ". " +
                        basicTokenSetClass.getName() + "contains " + basicTokenSetName);
      }

      if (tokenSet != basicTokenSetsEntry.getValue()) {
        Assertions.fail("TokenSets with names :" + basicTokenSetName + " and " + expectedBasicTokenSetName + " from " +
                        mainTokenSetClass.getName() + " and " + basicTokenSetClass.getName() + " are different");
      }
    }

    for (Map.Entry<String, ParentAwareTokenSet> basicTokenSetsEntry : basicParentTokenSets.entrySet()) {
      String basicTokenSetName = basicTokenSetsEntry.getKey();
      if (!basicTokenSetName.startsWith(BASIC)) {
        Assertions.fail(
          "Name of ParentAwareTokenSet " + basicTokenSetName + " doesn't start with " + BASIC + " in " + basicTokenSetClass.getName());
      }
      String expectedBasicTokenSetName = basicTokenSetName.substring(BASIC.length());
      TokenSet tokenSet = mainTokenSets.get(expectedBasicTokenSetName);
      if (tokenSet == null) {
        Assertions.fail(mainTokenSetClass.getName() + " doesn't contain TokenSet with name " + expectedBasicTokenSetName + ". " +
                        basicTokenSetClass.getName() + "contains " + basicTokenSetName);
      }

      Set<IElementType> parentContained = getContained(basicTokenSetsEntry.getValue(), allLoadedTypes);
      Set<IElementType> contained = getContained(tokenSet, allLoadedTypes);
      if (!parentContained.containsAll(contained)) {
        contained.removeAll(parentContained);
        Assertions.fail("ParentAwareTokenSet " +
                        basicTokenSetName  +
                        " doesn't contains all IElementType, which TokenSet " +
                        expectedBasicTokenSetName +
                        " contains. " +
                        "see " +
                        basicTokenSetClass.getName() +
                        " and " +
                        mainTokenSetClass.getName() +
                        ". Difference: " +
                        contained.stream().map(t -> t.getDebugName()).collect(Collectors.joining(", ")));
      }

      Set<IElementType> additionalSet = new HashSet<>();
      for (IElementType elementType : contained) {
        if (elementType instanceof ParentProviderElementType parentProviderElementType) {
          additionalSet.addAll(parentProviderElementType.getParents());
        }
      }
      contained.addAll(additionalSet);
      if (!contained.containsAll(parentContained)) {
        parentContained.removeAll(contained);
        Assertions.fail("TokenSet " +
                        expectedBasicTokenSetName +
                        " doesn't contains all IElementType, which ParentAwareTokenSet " +
                        basicTokenSetName +
                        " contains. " +
                        "see " +
                        basicTokenSetClass.getName() +
                        " and " +
                        mainTokenSetClass.getName() +
                        ". Difference: " +
                        parentContained.stream().map(t -> t.getDebugName()).collect(Collectors.joining(", ")));
      }
    }
  }

  private static Set<IElementType> getContained(ParentAwareTokenSet set, IElementType[] types) {
    Set<IElementType> result = new HashSet<>();
    for (IElementType type : types) {
      if (set.contains(type)) {
        result.add(type);
      }
    }
    return result;
  }

  private static Set<IElementType> getContained(TokenSet set, IElementType[] types) {
    Set<IElementType> result = new HashSet<>();
    for (IElementType type : types) {
      if (set.contains(type)) {
        result.add(type);
      }
    }
    return result;
  }

  private static <T> Map<String, T> getFieldsByName(Class<?> aClass, Class<T> targetType) throws IllegalAccessException {
    Field[] fields = aClass.getDeclaredFields();
    HashMap<String, T> result = new HashMap<>();
    for (Field field : fields) {
      field.setAccessible(true);
      Class<?> fieldType = field.getType();
      if (fieldType == targetType) {
        String name = field.getName();
        Object object = field.get(null);
        result.put(name, (T)object);
      }
    }
    return result;
  }

  private static void checkTokenSetContains(Class<?> mainTokenSet, Class<?> elements) throws IOException {
    String pathToModule = FileUtil.toSystemDependentName("/../../java-psi-impl/src/");
    String fullPath = PathManagerEx.getTestDataPath() + pathToModule;
    String pathToClass = mainTokenSet.getName();
    checkContains(fullPath + FileUtil.toSystemDependentName(pathToClass.replace(".", "/") + ".java"), elements.getSimpleName());
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

    Assert.assertNotEquals("Lexer and element types must be placed in different folders", lexerPath, backJavaElementTypePath);
    Assert.assertNotEquals("Lexer and element types must be placed in different folders", parserPath, backJavaElementTypePath);

    //not used in parser and lexer
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

      // exclude, contains refs to `BasicJavaElementTypeConverter` which is mixed up with `BasicJavaElementType`
      if (path.toString().endsWith("JavaBinaryOperations.kt")) {
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
