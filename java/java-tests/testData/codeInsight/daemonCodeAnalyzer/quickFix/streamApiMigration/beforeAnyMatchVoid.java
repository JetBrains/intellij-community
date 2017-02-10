// "Replace with anyMatch()" "true"

public class Main {
  void contains(String[][] haystack, String needle) {
    if(haystack != null) {
      for (String[] row : haysta<caret>ck) {
        if (row == null) continue;
        for (String str : row) {
          if (needle.equals(str))
            return;
        }
      }
    }
    throw new IllegalStateException();
  }
}
