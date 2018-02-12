// "Replace with anyMatch()" "true"

public class Main {
  boolean contains(String[][] haystack, String needle) {
    if(haystack != null) {
      for (String[] row : haysta<caret>ck) {
        if (row == null) continue;
        for (String str : row) {
          if (needle.equals(str))
            return true;
        }
      }
      System.out.println("Oops");
    }
    return false;
  }
}
