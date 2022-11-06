// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find;

import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.testFramework.fixtures.EditorMouseFixture;

public class FindInEditorMultiCaretTest extends AbstractFindInEditorTest {
  public void testBasic() {
    init("""
           abc
           abc
           abc""");
    initFind();
    setTextToFind("b");
    checkResultByText("""
                        a<selection>b<caret></selection>c
                        abc
                        abc""");
    addOccurrence();
    checkResultByText("""
                        a<selection>b<caret></selection>c
                        a<selection>b<caret></selection>c
                        abc""");
    nextOccurrence();
    checkResultByText("""
                        a<selection>b<caret></selection>c
                        abc
                        a<selection>b<caret></selection>c""");
    prevOccurrence();
    checkResultByText("""
                        a<selection>b<caret></selection>c
                        a<selection>b<caret></selection>c
                        abc""");
    removeOccurrence();
    checkResultByText("""
                        a<selection>b<caret></selection>c
                        abc
                        abc""");
    allOccurrences();
    checkResultByText("""
                        a<selection>b<caret></selection>c
                        a<selection>b<caret></selection>c
                        a<selection>b<caret></selection>c""");
    assertNull(getEditorSearchComponent());
  }

  public void testActionsInEditorWorkIndependently() {
    init("""
           abc
           abc
           abc""");
    initFind();
    setTextToFind("b");
    checkResultByText("""
                        a<selection>b<caret></selection>c
                        abc
                        abc""");
    new EditorMouseFixture((EditorImpl)myFixture.getEditor()).clickAt(0, 1);
    addOccurrenceFromEditor();
    addOccurrenceFromEditor();
    checkResultByText("""
                        <selection>a<caret>bc</selection>
                        <selection>a<caret>bc</selection>
                        abc""");
    nextOccurrenceFromEditor();
    checkResultByText("""
                        <selection>a<caret>bc</selection>
                        abc
                        <selection>a<caret>bc</selection>""");
    prevOccurrenceFromEditor();
    checkResultByText("""
                        <selection>a<caret>bc</selection>
                        <selection>a<caret>bc</selection>
                        abc""");
    removeOccurrenceFromEditor();
    checkResultByText("""
                        <selection>a<caret>bc</selection>
                        abc
                        abc""");
    allOccurrencesFromEditor();
    checkResultByText("""
                        <selection>a<caret>bc</selection>
                        <selection>a<caret>bc</selection>
                        <selection>a<caret>bc</selection>""");
    assertNotNull(getEditorSearchComponent());
  }

  public void testCloseRetainsMulticaretSelection() {
    init("""
           abc
           abc
           abc""");
    initFind();
    setTextToFind("b");
    addOccurrence();
    closeFind();
    checkResultByText("""
                        a<selection>b<caret></selection>c
                        a<selection>b<caret></selection>c
                        abc""");
  }

  public void testTextModificationRemovesOldSelections() {
    init("""
           abc
           abc
           abc""");
    initFind();
    setTextToFind("b");
    addOccurrence();
    setTextToFind("bc");

    assertEquals(1, myFixture.getEditor().getCaretModel().getCaretCount());
    assertEquals("bc", myFixture.getEditor().getSelectionModel().getSelectedText());
  }

  public void testSecondFindNavigatesToTheSameOccurrence() {
    init("""
           ab<caret>c
           abc
           abc""");
    initFind();
    setTextToFind("abc");
    checkResultByText("""
                        abc
                        <selection>abc<caret></selection>
                        abc""");
    closeFind();
    initFind();
    setTextToFind("abc");
    checkResultByText("""
                        abc
                        <selection>abc<caret></selection>
                        abc""");
  }
  
  public void testFindNextRetainsOnlyOneCaretIfNotUsedAsMoveToNextOccurrence() {
    init("<caret>To be or not to be?");
    initFind();
    setTextToFind("be");
    checkResultByText("To <selection>be<caret></selection> or not to be?");
    closeFind();
    new EditorMouseFixture((EditorImpl)myFixture.getEditor()).alt().shift().clickAt(0, 8); // adding second caret
    checkResultByText("To <selection>be<caret></selection> or<caret> not to be?");
    nextOccurrenceFromEditor();
    checkResultByText("To be or not to <selection>be<caret></selection>?");
  }

  public void testSelectAllDuringReplace() {
    init("some text");
    initReplace();
    setTextToFind("e");
    allOccurrences();
    checkResultByText("som<selection>e<caret></selection> t<selection>e<caret></selection>xt");
  }
}
