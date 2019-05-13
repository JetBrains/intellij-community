// "Fix all 'Loop can be collapsed with Stream API' problems in file" "true"

public class Test {
  public String test(String other) {
    for(String s : new <caret>String[] {"aaa", "bbb", "ccc", "ddd"}) {
      if(other.startsWith(s)) {
        return s;
      }
    }
    return null;
  }

  public CharSequence test2(String other) {
    for(CharSequence s : new CharSequence[] {"aaa", "bbb", "ccc", "ddd"}) {
      if(other.startsWith(s.toString())) {
        return s;
      }
    }
    return null;
  }

  public int test(int other) {
    for(int i : new int[] {2, 4, 8, 16, 32, 64, 128, 256, 512, 1024}) {
      if(i > other) {
        return i;
      }
    }
    return -1;
  }
}
