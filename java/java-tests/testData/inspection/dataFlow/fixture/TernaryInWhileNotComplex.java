import java.util.List;

class Foo {
  public void matchAfterFragment(int patternIndex, int matchLen) {
    int star = patternIndex < matchLen ? matchLen : -1;
    while (matchLen > 0) {
      int i = matchLen == star ? matchLen : star;
    }
  }

  public void matchAfterFragmentBoxed(Integer patternIndex, Integer matchLen) {
    Integer star = patternIndex < matchLen ? matchLen : -1;
    while (matchLen > 0) {
      Integer i = matchLen == star ? matchLen : star;
    }
  }

  public void matchAfterFragmentSemiBoxed(Integer patternIndex, Integer matchLen) {
    int star = patternIndex < matchLen ? matchLen : -1;
    while (matchLen > 0) {
      Integer i = matchLen == star ? matchLen : star;
    }
  }

}
