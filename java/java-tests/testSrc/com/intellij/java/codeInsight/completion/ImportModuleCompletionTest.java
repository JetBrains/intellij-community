// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase;
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.ui.JBColor;
import org.intellij.lang.annotations.Language;

import java.awt.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.*;

public class ImportModuleCompletionTest extends LightJava9ModulesCodeInsightFixtureTestCase {
  @NeedsIndex.Full
  public void testModuleImportDeclarations() {
    addJavaFile("module-info.java", "module M2 { }", M2);
    complete("Test.java",
             """
               import module M<caret>
               class Test { }
               """,
             """
               import module M2;<caret>
               class Test { }
               """);
  }

  @NeedsIndex.Full
  public void testAutoModuleImportDeclarations() {
    addFile(JarFile.MANIFEST_NAME, "Manifest-Version: 1.0\nAutomatic-Module-Name: all.fours\n", M4);
    complete("Test.java",
             """
               import module al<caret>
               class Test { }
               """,
             """
               import module all.fours;<caret>
               class Test { }
               """);
  }

  @NeedsIndex.Full
  public void testModuleImportDeclarationsBare() {
    addJavaFile("module-info.java", "module M2 { }", M2);
    addFile(JarFile.MANIFEST_NAME, "Manifest-Version: 1.0\nAutomatic-Module-Name: all.fours\n", M4);

    addJavaFile("module-info.java", "module current.module.name { }", MAIN);
    variants("Test.java",
             """
                 import module <caret>
                 class Test { }
               """,
             "M2", "java.base", "java.non.root", "java.se", "java.xml.bind", "java.xml.ws",
             "lib.multi.release", "lib.named", "lib.auto", "lib.claimed", "all.fours", "lib.with.module.info", "current.module.name");
  }

  @NeedsIndex.Full
  public void testModuleImportDeclarationsUseOwnModule() {
    addJavaFile("module-info.java", "module current.module.name { }", MAIN);
    complete("Test.java",
             """
                 import module current.<caret>
                 public class Test { }
               """,
             """
                 import module current.module.name;
                 public class Test { }
               """);
  }

  @NeedsIndex.Full
  public void testModuleImportDeclarationsUseOwnModule2() {
    complete("module-info.java",
             """
                 import module current.<caret>
                 module current.module.name { }
               """,
             """
                 import module current.module.name;
                 module current.module.name { }
               """);
  }

  @NeedsIndex.Full
  public void testModuleImportDeclarationsUseOwnModule3() {
    complete("module-info.java",
             """
                 import module curr<caret>
                 module current
                   .module .name { }
               """,
             """
                 import module current.module.name;
                 module current
                   .module .name { }
               """);
  }

  @NeedsIndex.Full
  public void testModuleImportDeclarationsUseOwnModule4() {
    complete("module-info.java",
             """
                 import module curr<caret>
                 import module java.base;
                 module current.module.name { }
               """,
             """
                 import module current.module.name;
                 import module java.base;
                 module current.module.name { }
               """);
  }

  @NeedsIndex.Full
  public void testModuleImportDeclarationsOrder() {
    addJavaFile("module-info.java",
                """
                    module first.module.name {
                      exports first.module.name;
                    }
                  """, M2);
    addJavaFile("MyClassA.java",
                """
                    package first.module.name;
                    public class MyClassA { }
                  """, M2);

    addJavaFile("module-info.java",
                """
                    module second.module.name {
                      exports second.module.name;
                    }
                  """, M4);
    addJavaFile("MyClassB.java",
                """
                    package second.module.name;
                    public class MyClassB { }
                  """, M4);

    addJavaFile("MyClassC.java",
                """
                    package current.pkg.name;
                    public class MyClassC { }
                  """, MAIN);
    @Language("JAVA") String code = """
        import module second.module.name;
        import current.pkg.name.*;
      
        public class Main {
          public static void main(String[] args) {
            MyCla<caret>
          }
        }
      """;
    myFixture.configureByText("Main.java", code);

    myFixture.complete(CompletionType.BASIC);
    myFixture.getLookup();
    myFixture.assertPreferredCompletionItems(0, "MyClassC", "MyClassB", "MyClassA");
  }

  @NeedsIndex.Full
  public void testTransitiveModuleImportDeclarationsOrder() {
    addJavaFile("module-info.java",
                """
                    module first.module.name {
                      requires transitive second.module.name;
                    }
                  """, M2);

    addJavaFile("module-info.java",
                """
                    module second.module.name {
                      exports second.module.name;
                    }
                  """, M4);
    addJavaFile("MyClassB.java",
                """
                    package second.module.name;
                    public class MyClassB { }
                  """, M4);

    addJavaFile("module-info.java",
                """
                    module third.module.name {
                      exports third.module.name;
                    }
                  """, M5);
    addJavaFile("MyClassC.java",
                """
                    package third.module.name;
                    public class MyClassC { }
                  """, M4);


    addJavaFile("module-info.java",
                """
                    module current.module.name {
                      requires first.module.name;
                    }
                  """, M4);

    @Language("JAVA") String code = """
        import module second.module.name;
        import current.pkg.name.*;
      
        public class Main {
          public static void main(String[] args) {
            MyCla<caret>
          }
        }
      """;
    myFixture.configureByText("Main.java", code);

    myFixture.complete(CompletionType.BASIC);
    myFixture.getLookup();
    myFixture.assertPreferredCompletionItems(0, "MyClassB", "MyClassC");
  }

  @NeedsIndex.Full
  public void testReadableCompletion1() {
    addJavaFile("module-info.java", "module current.module.name { requires first.module.name; }", MAIN);
    addJavaFile("module-info.java", "module first.module.name { }", M2);
    addJavaFile("module-info.java", "module second.module.name { }", M4);

    complete("MyClass.java",
             """
                 import module module<caret>
                 public class MyClass { }
               """, Map.of("current.module.name", JBColor.foreground(),
                           "first.module.name", JBColor.foreground(),
                           "second.module.name", JBColor.RED));
  }

  @NeedsIndex.Full
  public void testReadableCompletion2() {
    addJavaFile("module-info.java", "module current.module.name { requires first.module.name; }", MAIN);
    addFile(JarFile.MANIFEST_NAME, "Manifest-Version: 1.0\nAutomatic-Module-Name: first.module.name\n", M2);
    addFile(JarFile.MANIFEST_NAME, "Manifest-Version: 1.0\nAutomatic-Module-Name: second.module.name\n", M4);

    complete("MyClass.java",
             """
                 import module module<caret>
                 public class MyClass { }
               """, Map.of("current.module.name", JBColor.foreground(),
                           "first.module.name", JBColor.foreground(),
                           "second.module.name", JBColor.RED));
  }

  @NeedsIndex.Full
  public void testReadableCompletion3() {
    addJavaFile("module-info.java", "module first.module.name { }", M2);
    addJavaFile("module-info.java", "module second.module.name { }", M3);

    complete("MyClass.java",
             """
                 import module module<caret>
                 public class MyClass { }
               """, Map.of("first.module.name", JBColor.foreground(),
                           "second.module.name", JBColor.RED));
  }

  @NeedsIndex.Full
  public void testReadableCompletion4() {
    addFile(JarFile.MANIFEST_NAME, "Manifest-Version: 1.0\nAutomatic-Module-Name: first.module.name\n", M2);
    addFile(JarFile.MANIFEST_NAME, "Manifest-Version: 1.0\nAutomatic-Module-Name: second.module.name\n", M3);

    complete("MyClass.java",
             """
                 import module module<caret>
                 public class MyClass { }
               """, Map.of("first.module.name", JBColor.foreground(),
                           "second.module.name", JBColor.RED));
  }

  @NeedsIndex.Full
  public void testReadableCompletionTransitive() {
    addJavaFile("module-info.java", "module current.module.name { requires first.module.name; }", MAIN);
    addJavaFile("module-info.java", "module first.module.name { requires transitive second.module.name; }", M2);
    addJavaFile("module-info.java", "module second.module.name { requires transitive third.module.name; }", M4);
    addFile(JarFile.MANIFEST_NAME, "Manifest-Version: 1.0\nAutomatic-Module-Name: third.module.name\n", M5);
    addDependency(M2, M4);
    addDependency(M4, M5);

    complete("MyClass.java",
             """
                 import module module<caret>
                 public class MyClass { }
               """, Map.of("current.module.name", JBColor.foreground(),
                           "first.module.name", JBColor.foreground(),
                           "second.module.name", JBColor.foreground(),
                           "third.module.name", JBColor.foreground()));
  }

  private void addDependency(ModuleDescriptor from, ModuleDescriptor to) {
    ModuleManager moduleManager = ModuleManager.getInstance(getProject());
    Module fromModule = moduleManager.findModuleByName(from.getModuleName$intellij_java_tests());
    Module toModule = moduleManager.findModuleByName(to.getModuleName$intellij_java_tests());
    ModuleRootModificationUtil.addDependency(fromModule, toModule);
  }

  private void complete(String fileName, @Language("JAVA") String text, @Language("JAVA") String expected) {
    myFixture.configureByText(fileName, text);
    myFixture.completeBasic();
    myFixture.checkResult(expected);
  }

  private void complete(String fileName, @Language("JAVA") String text, Map<String, Color> expected) {
    myFixture.configureByText(fileName, text);
    myFixture.completeBasic();

    myFixture.getLookup();

    Map<String, Color> items = Arrays.stream(myFixture.getLookupElements()).map(lookup -> NormalCompletionTestCase.renderElement(lookup))
      .collect(Collectors.toMap(e -> e.getItemText(), e -> e.getItemTextForeground()));

    StringBuilder error = new StringBuilder();
    expected.forEach((name, color) -> {
      if (!Objects.equals(color, items.get(name))) {
        error.append(name).append(": ")
          .append(color).append(" != ").append(items.get(name))
          .append("\n");
      }
    });

    if (!error.isEmpty()) {
      fail(error.toString());
    }
  }

  private void variants(String fileName, @Language("JAVA") String text, String... variants) {
    myFixture.configureByText(fileName, text);
    myFixture.completeBasic();
    org.assertj.core.api.Assertions.assertThat(myFixture.getLookupElementStrings()).containsExactlyInAnyOrder(variants);
  }

  private void addJavaFile(String path, @Language("JAVA") String text, ModuleDescriptor module) {
    addFile(path, text, module);
  }
}
