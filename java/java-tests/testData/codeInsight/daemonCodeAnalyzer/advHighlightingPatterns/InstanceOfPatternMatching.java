
class Main {

  private static final boolean TRUE = 1 == 1;

  void testParenthesizedPattern(String s) {
    if (s instanceof (String s1)) {
      System.out.println(s1);
    }
  }

  void testDeepParenthesizedPattern(String s) {
    if (s instanceof ( ((( ((  String s1)) ))     ) )) {
      System.out.println(s1);
    }
  }
}