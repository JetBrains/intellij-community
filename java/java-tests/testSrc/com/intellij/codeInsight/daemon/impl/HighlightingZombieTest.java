// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

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

final class HighlightingZombieTest {

  @Test
  void testHighlightingZombieSerDe() throws IOException {
    HighlightingZombie zombie = createHighlightingZombie();
    byte[] bytes = serializedZombie(zombie);
    HighlightingZombie deserializedZombie = deserializedZombie(bytes);
    assertEquals(zombie, deserializedZombie);
  }

  private static HighlightingZombie createHighlightingZombie() throws MalformedURLException {
    return new HighlightingZombie(
      List.of(
        new HighlightingLimb(
          10,
          20,
          30,
          HighlighterTargetArea.LINES_IN_RANGE,
          TextAttributesKey.find("CLASS_NAME_ATTRIBUTES"),
          null,
          new CachedImageIcon(new URL("file:///example"), null)
        ),
        new HighlightingLimb(
          40,
          50,
          60,
          HighlighterTargetArea.EXACT_RANGE,
          null,
          new TextAttributes(
            new Color(100),
            null,
            new Color(200),
            EffectType.STRIKEOUT,
            Font.BOLD
          ),
          null
        )
      )
    );
  }

  private static byte[] serializedZombie(HighlightingZombie zombie) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(baos)) {
      HighlightingNecromancy.INSTANCE.buryZombie(output, zombie);
    }
    return baos.toByteArray();
  }

  private static HighlightingZombie deserializedZombie(byte[] zombieBytes) throws IOException {
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(zombieBytes))) {
      return HighlightingNecromancy.INSTANCE.exhumeZombie(input);
    }
  }

   //special handling for GutterIcon
  private static void assertEquals(HighlightingZombie expected, HighlightingZombie actual) {
    Assertions.assertEquals(expected, expected);
    List<HighlightingLimb> expectedHighlighters = expected.limbs();
    List<HighlightingLimb> actualHighlighters = actual.limbs();
    for (int i = 0; i < expectedHighlighters.size(); i++) {
      Icon expectedIcon = expectedHighlighters.get(i).getGutterIcon();
      Icon actualIcon = actualHighlighters.get(i).getGutterIcon();
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
