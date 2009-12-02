/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.psi.impl.compiled.SignatureParsing;
import junit.framework.TestCase;

import java.text.StringCharacterIterator;

public class SignatureParsingTest extends TestCase {
  public void testVarianceAmbiguity() throws Exception {
    assertEquals("Psi<?,P>", SignatureParsing.parseTypeString(new StringCharacterIterator("LPsi<*TP>;")));
    assertEquals("Psi<? extends P>", SignatureParsing.parseTypeString(new StringCharacterIterator("LPsi<+TP>;")));
    assertEquals("Psi<? super P>", SignatureParsing.parseTypeString(new StringCharacterIterator("LPsi<-TP>;")));
  }
}
