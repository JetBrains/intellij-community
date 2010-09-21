package com.intellij.psi.formatter;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertSame;

/**
 * @author Denis Zhdanov
 * @since 09/20/2010
 */
public class StaticSymbolWhiteSpaceDefinitionStrategyTest {

  private StaticSymbolWhiteSpaceDefinitionStrategy myStrategy;

  @Before
  public void setUp() {
    myStrategy = new StaticSymbolWhiteSpaceDefinitionStrategy('a', 'b', 'c');
  }

  @Test
  public void failOnTheFirstSymbol() {
    assertSame(0, myStrategy.check("def", 0, 2));
    assertSame(1, myStrategy.check("defghi", 1, 2));
  }

  @Test
  public void failInTheMiddle() {
    assertSame(1, myStrategy.check("adef", 0, 3));
    assertSame(2, myStrategy.check("daefghi", 1, 3));
  }

  @Test
  public void failOnTheLastSymbol() {
    assertSame(2, myStrategy.check("abe", 0, 3));
    assertSame(3, myStrategy.check("dabefghi", 1, 4));
  }

  @Test
  public void successfulMatch() {
    assertSame(3, myStrategy.check("abc", 0, 3));
    assertSame(4, myStrategy.check("dabcefg", 1, 4));
  }
}
