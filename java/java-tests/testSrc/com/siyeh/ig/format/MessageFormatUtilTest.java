// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.format;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.containers.ContainerUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class MessageFormatUtilTest {

  @Test
  public void testQuotes() {
    List<MessageFormatUtil.MessageFormatError> errors = MessageFormatUtil.checkQuote("''");
    Assert.assertTrue(errors.isEmpty());

    errors = MessageFormatUtil.checkQuote("'''");
    Assert.assertTrue(errors.isEmpty());

    errors = MessageFormatUtil.checkQuote("a'''");
    Assert.assertTrue(errors.isEmpty());

    errors = MessageFormatUtil.checkQuote("gh.a'''i,,h");
    Assert.assertTrue(errors.isEmpty());

    errors = MessageFormatUtil.checkQuote("a'''b");
    Assert.assertTrue(errors.isEmpty());

    errors = MessageFormatUtil.checkQuote("i'''mh");
    Assert.assertTrue(errors.isEmpty());

    errors = MessageFormatUtil.checkQuote("I'm he's she'll");
    Assert.assertTrue(errors.isEmpty());

    errors = MessageFormatUtil.checkQuote("I'''m");
    Assert.assertEquals(1, errors.size());
    MessageFormatUtil.MessageFormatError error = errors.getFirst();
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.MANY_QUOTES, error.errorType());
    Assert.assertEquals(1, error.fromIndex());
    Assert.assertEquals(4, error.toIndex());


    errors = MessageFormatUtil.checkQuote("I'''m. He'''s");
    Assert.assertEquals(2, errors.size());
    error = errors.getFirst();
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.MANY_QUOTES, error.errorType());
    Assert.assertEquals(1, error.fromIndex());
    Assert.assertEquals(4, error.toIndex());
    error = errors.get(1);
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.MANY_QUOTES, error.errorType());
    Assert.assertEquals(9, error.fromIndex());
    Assert.assertEquals(12, error.toIndex());


    errors = MessageFormatUtil.checkQuote("df. I'''m. He'''s, ghjg");
    Assert.assertEquals(2, errors.size());
    error = errors.getFirst();
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.MANY_QUOTES, error.errorType());
    Assert.assertEquals(5, error.fromIndex());
    Assert.assertEquals(8, error.toIndex());
    error = errors.get(1);
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.MANY_QUOTES, error.errorType());
    Assert.assertEquals(13, error.fromIndex());
    Assert.assertEquals(16, error.toIndex());
  }

  @Test
  public void testSimplePatterns() {
    MessageFormatUtil.MessageFormatResult result = MessageFormatUtil.checkFormat("", LanguageLevel.JDK_21);
    Assert.assertTrue(result.valid());

    result = MessageFormatUtil.checkFormat("123", LanguageLevel.JDK_21);
    Assert.assertTrue(result.valid());

    result = MessageFormatUtil.checkFormat("{0}{1}{2}{5}", LanguageLevel.JDK_21);
    Assert.assertTrue(result.valid());
    List<MessageFormatUtil.MessageFormatPlaceholder> placeholders = result.placeholders();
    Assert.assertEquals(4, placeholders.size());
    Assert.assertEquals(0, placeholders.get(0).index());
    Assert.assertEquals(1, placeholders.get(1).index());
    Assert.assertEquals(2, placeholders.get(2).index());
    Assert.assertEquals(5, placeholders.get(3).index());

    MessageFormatUtil.MessageHolder messageHolderResult = MessageFormatUtil.parseMessageHolder("'1{0}' {0} ''", LanguageLevel.JDK_21);
    Assert.assertTrue(messageHolderResult.getErrors().isEmpty());
    Assert.assertEquals(3, messageHolderResult.getParts().size());
    List<MessageFormatUtil.MessageFormatPart> parts = messageHolderResult.getParts();
    Assert.assertEquals(MessageFormatUtil.MessageFormatParsedType.STRING, parts.get(0).getParsedType());
    Assert.assertEquals("1{0} ", parts.get(0).getText());
    Assert.assertEquals(MessageFormatUtil.MessageFormatParsedType.FORMAT_ELEMENT, parts.get(1).getParsedType());
    Assert.assertEquals("{0}", parts.get(1).getText());
    Assert.assertEquals(0, parts.get(1).getMessageFormatElement().getIndex().intValue());
    Assert.assertEquals(MessageFormatUtil.MessageFormatParsedType.STRING, parts.get(2).getParsedType());
    Assert.assertEquals(" '", parts.get(2).getText());

    messageHolderResult = MessageFormatUtil.parseMessageHolder("{0, number, '#'{}'{'#,00  }", LanguageLevel.JDK_21);
    Assert.assertTrue(messageHolderResult.getErrors().isEmpty());
    parts = messageHolderResult.getParts().stream().filter(t -> !t.getText().isEmpty()).toList();
    Assert.assertEquals(1, parts.size());
    MessageFormatUtil.MessageFormatPart part = parts.getFirst();
    Assert.assertEquals(MessageFormatUtil.MessageFormatParsedType.FORMAT_ELEMENT, part.getParsedType());
    Assert.assertEquals("{0,number, '#'{}'{'#,00  }", part.getText());
    Assert.assertEquals(0, part.getMessageFormatElement().getIndex().intValue());


    result = MessageFormatUtil.checkFormat("{0}12345'{0,   number, #.00'", LanguageLevel.JDK_21);
    Assert.assertTrue(result.valid());
    Assert.assertEquals(1, result.placeholders().size());
  }

  @Test
  public void testErrors() {
    MessageFormatUtil.MessageFormatResult result = MessageFormatUtil.checkFormat("01234567890{ab}", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    List<MessageFormatUtil.MessageFormatError> errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.UNPARSED_INDEX, errors.getFirst().errorType());
    Assert.assertEquals(12, errors.getFirst().fromIndex());
    Assert.assertEquals(14, errors.getFirst().toIndex());

    result = MessageFormatUtil.checkFormat("01234567890{-1}", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.INDEX_NEGATIVE, errors.getFirst().errorType());
    Assert.assertEquals(12, errors.getFirst().fromIndex());
    Assert.assertEquals(14, errors.getFirst().toIndex());

    result = MessageFormatUtil.checkFormat("01234567890{0,   wrongformat, #.00}", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.UNKNOWN_FORMAT_TYPE, errors.getFirst().errorType());
    Assert.assertEquals(14, errors.getFirst().fromIndex());
    Assert.assertEquals(28, errors.getFirst().toIndex());

    result = MessageFormatUtil.checkFormat("01234567890{0,   number, {}{#.00}", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    errors = result.errors().stream().filter(t -> t.errorType().getSeverity() == MessageFormatUtil.ErrorSeverity.RUNTIME_EXCEPTION).toList();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.UNMATCHED_BRACE, errors.getFirst().errorType());
    Assert.assertEquals(11, errors.getFirst().fromIndex());
    Assert.assertEquals(12, errors.getFirst().toIndex());
  }

  @Test
  public void testWeakWarnings() {
    MessageFormatUtil.MessageFormatResult result = MessageFormatUtil.checkFormat("{0}12345''''67890 it''''s{0,   number, #.00}", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    List<MessageFormatUtil.MessageFormatError> errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.MANY_QUOTES, errors.getFirst().errorType());
    Assert.assertEquals(20, errors.getFirst().fromIndex());
    Assert.assertEquals(24, errors.getFirst().toIndex());

    result = MessageFormatUtil.checkFormat("''''67890 it''''s''", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.MANY_QUOTES, errors.getFirst().errorType());
    Assert.assertEquals(12, errors.getFirst().fromIndex());
    Assert.assertEquals(16, errors.getFirst().toIndex());

    result = MessageFormatUtil.checkFormat("123 '{0, number, ''#.00}' 123", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.QUOTED_PLACEHOLDER, errors.getFirst().errorType());
    Assert.assertEquals(5, errors.getFirst().fromIndex());
    Assert.assertEquals(24, errors.getFirst().toIndex());

    result = MessageFormatUtil.checkFormat("'{1}'", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.QUOTED_PLACEHOLDER, errors.getFirst().errorType());
    Assert.assertEquals(1, errors.getFirst().fromIndex());
    Assert.assertEquals(4, errors.getFirst().toIndex());
  }

  @Test
  public void testWarnings() {
    MessageFormatUtil.MessageFormatResult result = MessageFormatUtil.checkFormat("{0}12345''''6789{0,   number{, #.00", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    List<MessageFormatUtil.MessageFormatError> errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.UNCLOSED_BRACE_PLACEHOLDER, errors.getFirst().errorType());
    Assert.assertEquals(16, errors.getFirst().fromIndex());
    Assert.assertEquals(17, errors.getFirst().toIndex());

    result = MessageFormatUtil.checkFormat("{0}12345'6789{0,   number, #.00}", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.UNPAIRED_QUOTE, errors.getFirst().errorType());
    Assert.assertEquals(8, errors.getFirst().fromIndex());
    Assert.assertEquals(9, errors.getFirst().toIndex());

    result = MessageFormatUtil.checkFormat("{0}12345'{0,   number, #.00}", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.UNPAIRED_QUOTE, errors.getFirst().errorType());
    Assert.assertEquals(8, errors.getFirst().fromIndex());
    Assert.assertEquals(9, errors.getFirst().toIndex());

    result = MessageFormatUtil.checkFormat("' 123", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.UNPAIRED_QUOTE, errors.getFirst().errorType());
    Assert.assertEquals(0, errors.getFirst().fromIndex());
    Assert.assertEquals(1, errors.getFirst().toIndex());

    result = MessageFormatUtil.checkFormat("'' 123'", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.UNPAIRED_QUOTE, errors.getFirst().errorType());
    Assert.assertEquals(6, errors.getFirst().fromIndex());
    Assert.assertEquals(7, errors.getFirst().toIndex());
  }

  @Test
  public void testChoiceSimple() {
    MessageFormatUtil.MessageHolder result =
      MessageFormatUtil.parseMessageHolder(
        "{0} choice: {0,   choice,-1#'''' - 2 quotes|0<more|2<''1{0}'' '''' - 1 quote|3≤{0, number, '#'.00}}", LanguageLevel.JDK_21);
    Assert.assertTrue(result.getErrors().isEmpty());
    Assert.assertTrue(ContainerUtil.exists(result.getParts(), t -> t.getText().equals("1{0} ' - 1 quote")));
    Assert.assertTrue(ContainerUtil.exists(result.getParts(), t -> t.getText().equals("'' - 2 quotes")));
    Assert.assertTrue(ContainerUtil.exists(result.getParts(), t -> t.getText().equals("{0,number, #.00}") && t.getParsedType() ==
                                                                                                             MessageFormatUtil.MessageFormatParsedType.FORMAT_ELEMENT));
    result =
      MessageFormatUtil.parseMessageHolder(
        "nested choice: {0, choice,0# inner'{0, choice,1#test1|2#test2}'|3# inner 2}}", LanguageLevel.JDK_21);
    Assert.assertTrue(result.getErrors().isEmpty());
    Assert.assertTrue(ContainerUtil.exists(result.getParts(), t -> t.getText().equals("test1")));
    Assert.assertTrue(ContainerUtil.exists(result.getParts(), t -> t.getText().equals("test2")));
    Assert.assertTrue(ContainerUtil.exists(result.getParts(), t -> t.getText().equals(" inner 2")));

    MessageFormatUtil.MessageFormatResult formatResult = MessageFormatUtil.checkFormat("nested choice: {0, choice, 0#1}", LanguageLevel.JDK_21);
    Assert.assertTrue(formatResult.valid());

    formatResult = MessageFormatUtil.checkFormat("{0} choice: {0,   choice,-1#'''' - 2 quotes|0<more|2<'''1'''}", LanguageLevel.JDK_21);
    Assert.assertTrue(formatResult.valid());
  }

  @Test
  public void testChoiceCompileError() {
    MessageFormatUtil.MessageFormatResult result = MessageFormatUtil.checkFormat(
      "{0} choice: {0,   choice,-1#'''' - 2 quotes|0<more|2<''{0}'' '''' - 1 quotes|3#{0, number, #.00}}", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    List<MessageFormatUtil.MessageFormatError> errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.SELECTOR_NOT_FOUND, errors.getFirst().errorType());
    Assert.assertEquals(91, errors.getFirst().fromIndex());
    Assert.assertEquals(92, errors.getFirst().toIndex());

    result = MessageFormatUtil.checkFormat(
      "{0}'' choice: {0,   choice,0<more|wrong<wrong}", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.INCORRECT_CHOICE_SELECTOR, errors.getFirst().errorType());
    Assert.assertEquals(34, errors.getFirst().fromIndex());
    Assert.assertEquals(39, errors.getFirst().toIndex());

    result = MessageFormatUtil.checkFormat(
      "{0}'' choice: {0,   choice,0<more|-1<wrong}", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.INCORRECT_ORDER_CHOICE_SELECTOR, errors.getFirst().errorType());
    Assert.assertEquals(34, errors.getFirst().fromIndex());
    Assert.assertEquals(36, errors.getFirst().toIndex());
  }


  @Test
  public void testChoiceNestedWarning() {
    MessageFormatUtil.MessageFormatResult result = MessageFormatUtil.checkFormat(
      "{0}'' choice: {0,   choice,0<more|1<''{0}''}", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    List<MessageFormatUtil.MessageFormatError> errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.QUOTED_PLACEHOLDER, errors.getFirst().errorType());
    Assert.assertEquals(38, errors.getFirst().fromIndex());
    Assert.assertEquals(41, errors.getFirst().toIndex());

    result = MessageFormatUtil.checkFormat(
      "{0}'' choice: {0,   choice,0<more|1<''{''}", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.UNMATCHED_BRACE, errors.getFirst().errorType());
    Assert.assertEquals(14, errors.getFirst().fromIndex());
    Assert.assertEquals(15, errors.getFirst().toIndex());

    result =
      MessageFormatUtil.checkFormat(
        "nested choice: {0, choice,0# inner'''{0, choice,1#test1|2#test2}'|3#1}}", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(34, errors.getFirst().fromIndex());
    Assert.assertEquals(37, errors.getFirst().toIndex());
  }

  @Test
  public void testRange() {
    MessageFormatUtil.MessageFormatResult result = MessageFormatUtil.checkFormat("{0, wrong}", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    List<MessageFormatUtil.MessageFormatError> errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.UNKNOWN_FORMAT_TYPE, errors.getFirst().errorType());
    Assert.assertEquals(3, errors.getFirst().fromIndex());
    Assert.assertEquals(9, errors.getFirst().toIndex());

    result = MessageFormatUtil.checkFormat("{0, wrong,}", LanguageLevel.JDK_21);
    Assert.assertFalse(result.valid());
    errors = result.errors();
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.UNKNOWN_FORMAT_TYPE, errors.getFirst().errorType());
    Assert.assertEquals(3, errors.getFirst().fromIndex());
    Assert.assertEquals(9, errors.getFirst().toIndex());
  }

  @Test
  public void testChoiceIncorrectOrder() {
    MessageFormatUtil.MessageFormatResult result = MessageFormatUtil.checkFormat("{0, choice,0#0|0<1}", LanguageLevel.JDK_21);
    Assert.assertTrue(result.valid());
  }

  @Test
  public void testJdk23FormatTypes() {
    String[] jdk23Types = {
      "dtf_date", "dtf_time", "dtf_datetime",
      "list",
      "BASIC_ISO_DATE",
      "ISO_LOCAL_DATE",
      "ISO_OFFSET_DATE" ,
      "ISO_DATE",
      "ISO_LOCAL_TIME",
      "ISO_OFFSET_TIME",
      "ISO_TIME",
      "ISO_LOCAL_DATE_TIME",
      "ISO_OFFSET_DATE_TIME",
      "ISO_ZONED_DATE_TIME",
      "ISO_DATE_TIME",
      "ISO_ORDINAL_DATE",
      "ISO_WEEK_DATE",
      "ISO_INSTANT",
      "RFC_1123_DATE_TIME"
    };

    for (String type : jdk23Types) {
      MessageFormatUtil.MessageFormatResult result = MessageFormatUtil.checkFormat("{0, " + type + "}", LanguageLevel.JDK_22);
      Assert.assertFalse(result.valid());
      Assert.assertEquals(1, result.errors().size());
      Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.UNKNOWN_FORMAT_TYPE_LANGUAGE_LEVEL, result.errors().getFirst().errorType());
    }

    for (String type : jdk23Types) {
      MessageFormatUtil.MessageFormatResult result = MessageFormatUtil.checkFormat("{0, " + type + "}", LanguageLevel.JDK_23);
      Assert.assertTrue(result.valid());
    }

    MessageFormatUtil.MessageFormatResult result = MessageFormatUtil.checkFormat("{0, totally_unknown}", LanguageLevel.JDK_23);
    Assert.assertFalse(result.valid());
    Assert.assertEquals(1, result.errors().size());
    Assert.assertEquals(MessageFormatUtil.MessageFormatErrorType.UNKNOWN_FORMAT_TYPE, result.errors().getFirst().errorType());

    // Classic types should still work in both modes
    for (String type : new String[]{"number", "date", "time", "choice"}) {
      result = MessageFormatUtil.checkFormat("{0, " + type + "}", LanguageLevel.JDK_22);
      Assert.assertTrue(result.valid());
      result = MessageFormatUtil.checkFormat("{0, " + type + "}", LanguageLevel.JDK_23);
      Assert.assertTrue(result.valid());
    }
  }
}
