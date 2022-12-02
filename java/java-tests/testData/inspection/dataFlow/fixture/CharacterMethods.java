import java.util.*;

class X {
  void test(char ch, int codePoint) {
    if (<warning descr="Condition 'Character.isHighSurrogate(ch) && Character.isLowSurrogate(ch)' is always 'false'">Character.isHighSurrogate(ch) && <warning descr="Condition 'Character.isLowSurrogate(ch)' is always 'false' when reached">Character.isLowSurrogate(ch)</warning></warning>) {

    }
    if (<warning descr="Condition 'Character.isBmpCodePoint(codePoint) && Character.isSupplementaryCodePoint(codePoint)' is always 'false'">Character.isBmpCodePoint(codePoint) && <warning descr="Condition 'Character.isSupplementaryCodePoint(codePoint)' is always 'false' when reached">Character.isSupplementaryCodePoint(codePoint)</warning></warning>) {

    }
    if (!Character.isValidCodePoint(codePoint)) {
      if (<warning descr="Condition 'Character.isBmpCodePoint(codePoint)' is always 'false'">Character.isBmpCodePoint(codePoint)</warning>) {
      }
      if (<warning descr="Condition 'Character.isSupplementaryCodePoint(codePoint)' is always 'false'">Character.isSupplementaryCodePoint(codePoint)</warning>) {
      }
    }
  }
}