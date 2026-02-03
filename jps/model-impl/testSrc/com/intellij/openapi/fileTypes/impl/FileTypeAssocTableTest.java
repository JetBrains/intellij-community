// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import java.util.List;

import static org.junit.Assert.assertNotEquals;

public class FileTypeAssocTableTest extends UsefulTestCase {

  // This should still pass even without the fix for IJPL-149806,  as wildcard patterns
  // are always tried before extension patterns (like this single-segment one).
  public void testWildcardPatternIsMoreSpecificThanExtensionPattern() {
    var table = createAssocTable(
      new Pair<>("*.bar", "foobar"),
      new Pair<>("*.foo.bar", "foobar (more specific)")
    );

    assertEquals(
      "foobar (more specific)",
      table.findAssociatedFileType("hurrdurr.foo.bar")
    );
  }

  // In cases where both patterns classify as wildcard patterns, the class needs
  // to take their relative specificity into account when finding a match.
  // We do this to resolve the aforementioned IJPL-149806 issue.

  // We consider longer patterns to be more specific than shorter patterns.
  public void testLongerWildcardPatternIsMoreSpecific() {
    var table = createAssocTable(
      new Pair<>("*.foo.bar", "foobar"),
      new Pair<>("*.bonk.foo.bar", "foobar (more specific)")
    );

    assertEquals(
      "foobar (more specific)",
      table.findAssociatedFileType("hurrdurr.bonk.foo.bar")
    );
  }

  // If the patterns are of equal length, compare them lexicographically except for * and ?,
  // which we treat as — respectively — least and next-to-least-specific characters.
  public void testLettersMoreSpecificThanWildcards() {
    var table = createAssocTable(
      new Pair<>("*.p??", "pdf"),
      new Pair<>("*.p*f", "pdf (more specific)"),
      new Pair<>("*.p?f", "pdf (most specific)")
    );

    assertEquals(
      "pdf (most specific)",
      table.findAssociatedFileType("ayy.pdf")
    );
  }

  // Because we have a stable ordering of patterns — first by length, then by lexicographical
  // comparison except wildcards, then by ? and then finally by * — then insertion order shouldn't
  // change which pattern is recognised as more specific
  public void testSpecificityDoesntDependOnInsertionOrder() {
    var table1 = createAssocTable(
      new Pair<>("*.p??", "pdf"),
      new Pair<>("*.p*f", "pdf (more specific)"),
      new Pair<>("*.p?f", "pdf (most specific)")
    );
    var table2 = createAssocTable(
      new Pair<>("*.p?f", "pdf (most specific)"),
      new Pair<>("*.p??", "pdf"),
      new Pair<>("*.p*f", "pdf (more specific)")
    );

    assertEquals(
      table1.findAssociatedFileType("ayy.pdf"),
      table2.findAssociatedFileType("ayy.pdf")
    );
  }

  // An unfortunate side-effect of this rather simple concept of pattern specificity is
  // that some patterns might be counterintuitively preferred over others, such as in this
  // case that is used to document the current behaviour of the class.
  public void testLongerWildcardPatternIsMoreSpecific22() {
    var table = createAssocTable(
      new Pair<>("*.foo.cpp", "cpp"),
      new Pair<>("env.foo.*", "env")
    );

    assertNotEquals(
      "cpp",
      table.findAssociatedFileType("env.foo.cpp")
    );
  }

  @SafeVarargs
  private static FileTypeAssocTable<String> createAssocTable(Pair<String, String>... associations) {
    var assocTable = new FileTypeAssocTable<String>();

    createAssocList(associations)
      .forEach(association -> assocTable.addAssociation(association.first, association.second));

    return assocTable;
  }

  @SafeVarargs
  private static List<Pair<FileNameMatcher, String>> createAssocList(Pair<String, String>... associations) {
    var factory = FileNameMatcherFactory.getInstance();

    return ContainerUtil.map(
      associations,
      association -> new Pair<>(factory.createMatcher(association.first), association.second)
    );
  }
}