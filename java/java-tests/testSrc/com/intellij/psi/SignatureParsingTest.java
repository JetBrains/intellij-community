/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi;

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
    parseTypeString("Psi<?,P>", "LPsi<*TP>;");
    parseTypeString("Psi<? extends P>", "LPsi<+TP>;");
    parseTypeString("Psi<? super P>", "LPsi<-TP>;");
  }

  @Test
  public void testMapping() throws ClsFormatException {
    parseTypeString("p.Obj.I<p.Obj1.I,p.Obj2.I>", "Lp/Obj$I<Lp/Obj1$I;Lp/Obj2$I;>;");
    parseTypeString("p.Obj$.I<p.$Obj1.I,p.Obj2.I$>", "Lp/Obj$$I<Lp/$Obj1$I;Lp/Obj2$I$;>;");
  }

  private static void parseTypeString(String expected, String signature) throws ClsFormatException {
    assertEquals(expected, SignatureParsing.parseTypeString(new StringCharacterIterator(signature), StubBuildingVisitor.GUESSING_MAPPER));
  }
}