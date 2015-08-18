/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
    assertEquals("Psi<?,P>", SignatureParsing.parseTypeString(new StringCharacterIterator("LPsi<*TP>;")));
    assertEquals("Psi<? extends P>", SignatureParsing.parseTypeString(new StringCharacterIterator("LPsi<+TP>;")));
    assertEquals("Psi<? super P>", SignatureParsing.parseTypeString(new StringCharacterIterator("LPsi<-TP>;")));
  }
}
