// "Fix all 'Redundant 'String' operation' problems in file" "true"

class StringBuilderToString {

  private static final String CONST_STRING_VAL = "Hello";
  static String str() { return ""; }

  void stringBuilderToStringSubstring() {
    String s1 = new StringBuilder().toString();
    String s2 = new StringBuilder()./* 1 */to<caret>String()/* 2 */.substring(1);
    String s3 = new StringBuilder()./* 1 */toString()/* 2 */.substring(1, 4);
    String s4 = new StringBuilder()./* 1 */toString()/* 2 */.substring(1, 4);
    int s5 = new StringBuilder()./* 1 */toString()/* 2 */.substring(1, 4).length();

    System.out.println(new StringBuilder()./* 1 */toString()/* 2 */);
    System.out.println(new StringBuilder()./* 1 */toString()/* 2 */.substring(1));
    System.out.println(new StringBuilder()./* 1 */toString()/* 2 */.substring(1, 3));
    System.out.println(new StringBuilder()./* 1 */toString()/* 2 */.substring(1, 3).length());
    System.out.println(new StringBuilder().substring(1, 3));
    System.out.println(new StringBuilder().substring(1, 3).length());

    "hello".substring(sb.toString());
    System.out.println((((new StringBuilder()))).substring(1, 3));
    System.out.println((new StringBuilder()).substring(1, 3).length());
  }

  void builder(StringBuilder sb) {
    String s1 = sb.toString();
    String s2 = sb./* 1 */toString()/* 2 */.substring(1);
    String s3 = sb./* 1 */toString()/* 2 */.substring(1, 4);
    String s4 = sb./* 1 */toString()/* 2 */.substring(1, 4);
    int s5 = sb./* 1 */toString()/* 2 */.substring(1, 4).length();

    System.out.println(sb./* 1 */toString()/* 2 */);
    System.out.println(sb./* 1 */toString()/* 2 */.substring(1));
    System.out.println(sb./* 1 */toString()/* 2 */.substring(1, 3));
    System.out.println(sb./* 1 */toString()/* 2 */.substring(1, 3).length());
    System.out.println(sb.substring(1, 3));
    System.out.println(sb.substring(1, 3).length());
  }
}
