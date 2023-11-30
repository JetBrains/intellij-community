// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.HighlightingMarkupGrave.FileMarkupInfo;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.icons.CachedImageIcon;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import static com.intellij.codeInsight.daemon.impl.HighlightingMarkupGrave.HighlighterState;

final class FileMarkupInfoTest {
  @Test
  void testFileMarkupInfoSerDe() throws IOException {
    FileMarkupInfo markupInfo = mockMarkupInfo();
    byte[] markupInfoBytes = serializedMarkupInfo(markupInfo);
    FileMarkupInfo actualMarkupInfo = deserializedMarkupInfo(markupInfoBytes);
    assertEquals(markupInfo, actualMarkupInfo);
  }

  private static FileMarkupInfo mockMarkupInfo() throws MalformedURLException {
    return new FileMarkupInfo(
      10,
      List.of(
        new HighlighterState(
          10,
          20,
          30,
          HighlighterTargetArea.LINES_IN_RANGE,
          TextAttributesKey.find("CLASS_NAME_ATTRIBUTES"),
          null,
          new CachedImageIcon(new URL("file:///example"), null)
        ),
        new HighlighterState(
          40,
          50,
          60,
          HighlighterTargetArea.EXACT_RANGE,
          null,
          new TextAttributes(new Color(100), null, new Color(200), EffectType.STRIKEOUT, Font.BOLD),
          null
        )
      )
    );
  }

  private static byte[] serializedMarkupInfo(FileMarkupInfo markupInfo) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(baos)) {
      markupInfo.bury(output);
    }
    return baos.toByteArray();
  }

  private static FileMarkupInfo deserializedMarkupInfo(byte[] markupInfoBytes) throws IOException {
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(markupInfoBytes))) {
      return FileMarkupInfo.exhume(input);
    }
  }

   //special handling for GutterIcon
  private static void assertEquals(FileMarkupInfo expected, FileMarkupInfo actual) {
    Assertions.assertEquals(expected, expected);
    List<HighlighterState> expectedHighlighters = expected.highlighters();
    List<HighlighterState> actualHighlighters = actual.highlighters();
    for (int i = 0; i < expectedHighlighters.size(); i++) {
      Icon expectedIcon = expectedHighlighters.get(i).gutterIcon();
      Icon actualIcon = actualHighlighters.get(i).gutterIcon();
      if (expectedIcon == null && actualIcon == null) {
        continue;
      }
      if (!(actualIcon instanceof CachedImageIcon actualCachedIcon)) {
        throw new AssertionError(actualIcon + " expected to be a CachedImageIcon");
      }
      Assertions.assertEquals(((CachedImageIcon) expectedIcon).getUrl(), actualCachedIcon.getUrl());
    }
  }
}
