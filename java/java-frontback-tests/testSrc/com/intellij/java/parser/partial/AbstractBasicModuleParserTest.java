// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicModuleParserTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicModuleParserTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-partial/modules", configurator);
  }

  @Test
  public void testSimple0() { doParserTest("module M1 { }"); }
  @Test
  public void testSimple1() { doParserTest("module a.b. /*here!*/ c { }"); }
  @Test
  public void testSimple2() { doParserTest("/* comment */\nmodule X { }"); }

  @Test
  public void testPackaged() { doParserTest("package pkg;\nmodule M { }"); }
  @Test
  public void testImports() { doParserTest("import java.lang.Deprecated;\nmodule M { }"); }

  @Test
  public void testModifierList0() { doParserTest("open module M { }"); }
  @Test
  public void testModifierList1() { doParserTest("@Deprecated module M { }"); }

  @Test
  public void testName0() { doParserTest("module A. { }"); }
  @Test
  public void testName1() { doParserTest("module A..B { }"); }
  @Test
  public void testName2() { doParserTest("module A B { }"); }
  @Test
  public void testName3() { doParserTest("module .A { }"); }

  @Test
  public void testIncomplete0() { doParserTest("module"); }
  @Test
  public void testIncomplete1() { doParserTest("module X"); }
  @Test
  public void testIncomplete2() { doParserTest("module {"); }
  @Test
  public void testIncomplete3() { doParserTest("module X {"); }
  @Test
  public void testIncomplete4() { doParserTest("module X { junk"); }

  @Test
  public void testBadContent() { doParserTest("module X {\n some junk\n}"); }
  @Test
  public void testExtras() { doParserTest("module one { }\nmodule another { }"); }
  @Test
  public void testSemicolons() { doParserTest("module M { ;;; }"); }

  @Test
  public void testRequires0() { doParserTest("module M { requires }"); }
  @Test
  public void testRequires1() { doParserTest("module M { requires X }"); }
  @Test
  public void testRequires2() { doParserTest("module M { requires transitive static X; }"); }
  @Test
  public void testRequires3() { doParserTest("module M { requires private X; }"); }
  @Test
  public void testRequires4() { doParserTest("module M { requires A, B; }"); }

  @Test
  public void testExports0() { doParserTest("module M { exports }"); }
  @Test
  public void testExports1() { doParserTest("module M { exports pkg }"); }
  @Test
  public void testExports2() { doParserTest("module M { exports pkg; }"); }
  @Test
  public void testExports3() { doParserTest("module M { exports pkg to }"); }
  @Test
  public void testExports4() { doParserTest("module M { exports pkg to ; }"); }
  @Test
  public void testExports5() { doParserTest("module M { exports pkg to A }"); }
  @Test
  public void testExports6() { doParserTest("module M { exports pkg to A, }"); }
  @Test
  public void testExports7() { doParserTest("module M { exports pkg to A, ; }"); }
  @Test
  public void testExports8() { doParserTest("module M { exports pkg to , B; }"); }

  @Test
  public void testOpens0() { doParserTest("module M { opens }"); }
  @Test
  public void testOpens1() { doParserTest("module M { opens pkg to A, B; }"); }

  @Test
  public void testUses0() { doParserTest("module M { uses }"); }
  @Test
  public void testUses1() { doParserTest("module M { uses C }"); }
  @Test
  public void testUses2() { doParserTest("module M { uses java.nio.file.spi.FileSystemProvider; }"); }

  @Test
  public void testProvides0() { doParserTest("module M { provides }"); }
  @Test
  public void testProvides1() { doParserTest("module M { provides Spi }"); }
  @Test
  public void testProvides2() { doParserTest("module M { provides Spi ; }"); }
  @Test
  public void testProvides3() { doParserTest("module M { provides Spi with }"); }
  @Test
  public void testProvides4() { doParserTest("module M { provides Spi with ; }"); }
  @Test
  public void testProvides5() { doParserTest("module M { provides Spi with Impl }"); }
  @Test
  public void testProvides6() { doParserTest("module M { provides Spi with Impl; }"); }
  @Test
  public void testProvides7() { doParserTest("module M { provides Spi _ }"); }
  @Test
  public void testProvides8() { doParserTest("module M { provides Spi with Impl1, Impl2, }"); }

  protected abstract void doParserTest(String text);
}