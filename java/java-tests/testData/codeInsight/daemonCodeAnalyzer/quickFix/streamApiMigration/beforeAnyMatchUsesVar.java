// "Replace with anyMatch()" "false"

public class Main {
  String contains(String[][] haystack, String needle) {
    if(haystack != null) {
      for (String[] row : haysta<caret>ck) {
        if (row == null) continue;
        for (String str : row) {
          if (needle.equals(str))
            return "yes"+str;
        }
      }
      System.out.println("oops");
    }
    return "no";
  }
}
