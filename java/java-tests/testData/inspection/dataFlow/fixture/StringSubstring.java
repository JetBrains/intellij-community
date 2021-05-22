import org.jetbrains.annotations.Nullable;

class StringSubstring {
  void testSubString(String s) {
    if (<warning descr="Condition 's.substring(1, 3).equals(\"_\")' is always 'false'">s.substring(1, 3).equals("_")</warning>) {}
    if (<warning descr="Condition 's.length() > 1' is always 'true'">s.length() > 1</warning>) {}
    if (<warning descr="Condition 's.substring(1).isEmpty()' is always 'false'">s.substring(1).isEmpty()</warning>) {}
  }

  @Nullable
  static String parseDir(String packageName, String dirName) {
    int index = packageName.length();
    while (index > 0) {
      int index1 = packageName.lastIndexOf('.', index - 1);
      String token = packageName.substring(index1 + 1, index);
      final boolean equalsToToken = dirName.equals(token);
      if (!equalsToToken) {
        String packagePrefix = packageName.substring(0, index);
        if (<warning descr="Condition 'packagePrefix.length() > 0' is always 'true'">packagePrefix.length() > 0</warning>) {
          return null;
        }
        return packagePrefix;
      }
      index = index1;
    }
    return null;
  }

  public static void parse(String text) {
    int xCnt = 0, yCnt = 0;
    int pos = text.length() - 1;
    for (; pos >= 0; --pos) {
      char ch = text.charAt(pos);
      if (ch == 'X' || ch == 'x') ++xCnt;
      else if (ch == 'Y' || ch == 'y') ++yCnt;
      else if (Character.isDigit(ch)) {
        ++pos;
        break;
      }
    }
    text = text.substring(0, pos);

    if (<warning descr="Condition 'text.length() == 0' is always 'false'">text.length() == 0</warning>) {
      throw new IllegalArgumentException();
    }
    System.out.println(xCnt + ":" + yCnt);
  }

  public void processPrefix(String text) {
    String currentPrefix = text.isEmpty() ? "^" : text.substring(0, 1);
    if (<warning descr="Condition '!currentPrefix.isEmpty()' is always 'true'">!<warning descr="Result of 'currentPrefix.isEmpty()' is always 'false'">currentPrefix.isEmpty()</warning></warning> && Character.isDigit(currentPrefix.charAt(0))) {
      currentPrefix = "";
    }
    System.out.println(currentPrefix);
  }

  void parseText(String text) {
    if (text.length() < 5) {
      System.out.println("Short");
    } else {
      String kind = text.substring(0, 2);
      String reference = text.substring(3);
      if (<warning descr="Condition 'reference.length() > 1' is always 'true'">reference.length() > 1</warning>) {}
    }
  }

  String getShortName(String fullName) {
    int end = fullName.lastIndexOf(".ext");
    if (end > 0) {
      String shortName = fullName.substring(0, end);
      fullName = <warning descr="Condition 'shortName.isEmpty()' is always 'false'">shortName.isEmpty()</warning> ? fullName : shortName;
    }
    return fullName;
  }

  void testEmpty(String input) {
    String s = input.substring(0, 1);
    String s1 = s.substring(1);
    if (<warning descr="Condition 's1.equals(\"\")' is always 'true'">s1.equals("")</warning>) {}
    String s2 = s1.substring(0);
    if (<warning descr="Condition 's2.equals(\"\")' is always 'true'">s2.equals("")</warning>) {}
    String s3 = s2.substring(0);
  }

  void testDiff(String s1, String s2, int pos) {
    if (<warning descr="Condition 's1.substring(pos, pos+4).equals(\"xyz\")' is always 'false'">s1.substring(pos, pos+4).equals("xyz")</warning>) {}
    if (s1.substring(pos, pos+4).equals(s2)) {
      if (<warning descr="Condition 's2.length() != 4' is always 'false'">s2.length() != 4</warning>) {}
    }
  }
  
  void testSubstringVarRef(String s, int from, int length) {
    String s1 = s.substring(0, length);
    if (<warning descr="Condition 's1.length() == length' is always 'true'">s1.length() == length</warning>) {}
    String s2 = s.substring(from, from + length);
    if (<warning descr="Condition 's2.length() == length' is always 'true'">s2.length() == length</warning>) {}
  }
}