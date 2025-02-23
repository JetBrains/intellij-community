// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi;

import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.compiled.SignatureParsing;
import com.intellij.psi.impl.compiled.StubBuildingVisitor;
import com.intellij.util.cls.ClsFormatException;
import org.junit.Test;

import java.text.StringCharacterIterator;

import static org.junit.Assert.assertEquals;

public class SignatureParsingTest {
  @Test
  public void testVarianceAmbiguity() throws ClsFormatException {
    assertEquals("Psi<?,P>", parseTypeString("LPsi<*TP>;"));
    assertEquals("Psi<? extends P>", parseTypeString("LPsi<+TP>;"));
    assertEquals("Psi<? super P>", parseTypeString("LPsi<-TP>;"));
  }

  @Test
  public void testMapping() throws ClsFormatException {
    assertEquals("p.Obj.I<p.Obj1.I,p.Obj2.I>", parseTypeString("Lp/Obj$I<Lp/Obj1$I;Lp/Obj2$I;>;"));
    assertEquals("p.Obj$.I<p.$Obj1.I,p.Obj2.I$>", parseTypeString("Lp/Obj$$I<Lp/$Obj1$I;Lp/Obj2$I$;>;"));
  }

  @Test
  public void testDollarInPackageName() throws ClsFormatException {
    assertEquals("autovalue.shaded.com.google$.common.collect.$ImmutableSet<java.lang.String>",
                 parseTypeString("Lautovalue/shaded/com/google$/common/collect/$ImmutableSet<Ljava/lang/String;>;"));
  }

  @Test(expected = ClsFormatException.class)
  public void testIllegal() throws ClsFormatException {
    parseTypeString("T");
  }

  private static String parseTypeString(String signature) throws ClsFormatException {
    String oldStyle = SignatureParsing.parseTypeString(new StringCharacterIterator(signature), StubBuildingVisitor.GUESSING_MAPPER);
    TypeInfo newStyle = SignatureParsing.parseTypeStringToTypeInfo(new SignatureParsing.CharIterator(signature), StubBuildingVisitor.GUESSING_PROVIDER);
    assertEquals(oldStyle, newStyle.text());
    return oldStyle;
  }
}