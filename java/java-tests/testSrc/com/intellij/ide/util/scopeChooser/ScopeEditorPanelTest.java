// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser;

import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.search.scope.packageSet.PackageSetFactory;
import com.intellij.psi.search.scope.packageSet.ParsingException;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
class ScopeEditorPanelTest {

  @Test
  void testIncludePackageSetSimple() throws ParsingException {
    PackageSet result = null;
    result = ScopeEditorPanel.doIncludeSelected(compile("file[A]:*/"), result);
    assertEquals("file[A]:*/", result.getText());
    result = ScopeEditorPanel.doIncludeSelected(compile("file[B]:*/"), result);
    assertEquals("file[A]:*/||file[B]:*/", result.getText());
    result = ScopeEditorPanel.doIncludeSelected(compile("file[C]:*/"), result);
    assertEquals("file[A]:*/||file[B]:*/||file[C]:*/", result.getText());
    result = ScopeEditorPanel.doIncludeSelected(compile("file[D]:*/"), result);
    assertEquals("file[A]:*/||file[B]:*/||file[C]:*/||file[D]:*/", result.getText());

    assertNull(ScopeEditorPanel.doIncludeSelected(compile("file[A]:*/"), compile("!file[A]:*/")));
    assertNull(ScopeEditorPanel.doIncludeSelected(compile("file[A]:*/"), compile("!file[A]:*/||!file[A]:*/")));
    assertNull(ScopeEditorPanel.doIncludeSelected(compile("file[A]:*/"), compile("!file[A]:*/&&!file[A]:*/")));
  }

  @Test
  void testExcludePackageSetSimple() throws ParsingException {
    PackageSet result = null;
    result = ScopeEditorPanel.doExcludeSelected(compile("file[A]:*/"), result);
    assertEquals("!file[A]:*/", result.getText());
    result = ScopeEditorPanel.doExcludeSelected(compile("file[B]:*/"), result);
    assertEquals("!file[A]:*/&&!file[B]:*/", result.getText());
    result = ScopeEditorPanel.doExcludeSelected(compile("file[C]:*/"), result);
    assertEquals("!file[A]:*/&&!file[B]:*/&&!file[C]:*/", result.getText());
    result = ScopeEditorPanel.doExcludeSelected(compile("file[D]:*/"), result);
    assertEquals("!file[A]:*/&&!file[B]:*/&&!file[C]:*/&&!file[D]:*/", result.getText());

    assertNull(ScopeEditorPanel.doExcludeSelected(compile("file[A]:*/"), compile("file[A]:*/")));
    assertNull(ScopeEditorPanel.doExcludeSelected(compile("file[A]:*/"), compile("file[A]:*/||file[A]:*/")));
    assertEquals("file[B]:*/", ScopeEditorPanel.doExcludeSelected(compile("file[A]:*/"), compile("file[A]:*/||file[B]:*/")).getText());
  }

  @Test
  void testComplex() throws ParsingException {
    PackageSet result = null;
    result = ScopeEditorPanel.doIncludeSelected(compile("file[A]:*/"), result);
    assertEquals("file[A]:*/", result.getText());
    result = ScopeEditorPanel.doIncludeSelected(compile("file[B]:*/"), result);
    assertEquals("file[A]:*/||file[B]:*/", result.getText());
    result = ScopeEditorPanel.doExcludeSelected(compile("file[C]:*/"), result);
    assertEquals("(file[A]:*/||file[B]:*/)&&!file[C]:*/", result.getText());
    result = ScopeEditorPanel.doIncludeSelected(compile("file[D]:*/"), result);
    assertEquals("(file[A]:*/||file[B]:*/)&&!file[C]:*/||file[D]:*/", result.getText());
    result = ScopeEditorPanel.doExcludeSelected(compile("file[E]:*/"), result);
    assertEquals("((file[A]:*/||file[B]:*/)&&!file[C]:*/||file[D]:*/)&&!file[E]:*/", result.getText());
  }

  private static PackageSet compile(String text) throws ParsingException {
    return PackageSetFactory.getInstance().compile(text);
  }
}