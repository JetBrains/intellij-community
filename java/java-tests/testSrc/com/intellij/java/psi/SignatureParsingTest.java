/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.psi;

import com.intellij.psi.impl.compiled.SignatureParsing;
import com.intellij.psi.impl.compiled.StubBuildingVisitor;
import com.intellij.util.cls.ClsFormatException;
import org.junit.Test;

import java.text.StringCharacterIterator;

import static org.junit.Assert.assertEquals;

/**
 * @author max
 */
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
    return SignatureParsing.parseTypeString(new StringCharacterIterator(signature), StubBuildingVisitor.GUESSING_MAPPER);
  }
}