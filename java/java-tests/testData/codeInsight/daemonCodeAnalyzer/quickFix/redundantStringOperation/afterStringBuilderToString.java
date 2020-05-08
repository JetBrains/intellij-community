// "Fix all 'Redundant String operation' problems in file" "true"

class StringBuilderToString {
  StringBuilderToString() {
    String s1 = new StringBuilder().toString();
      /* 1 */
      String s2 = new StringBuilder()/* 2 */.substring(1);
      /* 1 */
      String s3 = new StringBuilder()/* 2 */.substring(1, 4);
      /* 1 */
      String s4 = new StringBuilder()/* 2 */.substring(1, 4);
      /* 1 */
      int s5 = new StringBuilder()/* 2 */.substring(1, 4).length();

      /* 1 */
      System.out.println(new StringBuilder()/* 2 */);
      /* 1 */
      System.out.println(new StringBuilder()/* 2 */.substring(1));
      /* 1 */
      System.out.println(new StringBuilder()/* 2 */.substring(1, 3));
      /* 1 */
      System.out.println(new StringBuilder()/* 2 */.substring(1, 3).length());
    System.out.println(new StringBuilder().substring(1, 3));
    System.out.println(new StringBuilder().substring(1, 3).length());

    StringBuilder sb = new StringBuilder();
      /* 1 */
      String s6 = sb/* 2 */ + sb. /* 3 */toString()/* 4 */;
      /* 1 */
      String s7 = sb/* 2 */ + sb./* 3 */toString()/* 4 */;
    String s8 = "Hello, World" + sb.toString();
    String s8 = ("Hello, " + "World") + sb.toString();
    String s9 = sb./* 1 */toString()/* 2 */ + 42;
      /* 1 */
      String s10 = sb/* 2 */ + "Hello, World";
      /* 3 */
      /* 1 */
      String s11 = sb/* 2 */ + sb/* 4 */ + sb./* 5 */toString()/* 6 */;

      /* 1 */
      System.out.println(sb/* 2 */ + sb. /* 3 */toString()/* 4 */);
      /* 1 */
      System.out.println(sb/* 2 */ + sb./* 3 */toString()/* 4 */);
      /* 3 */
      /* 1 */
      System.out.println(sb/* 2 */ + sb/* 4 */ + sb./* 5 */toString()/* 6 */);
    System.out.println("Hello, World" + sb.toString());
    System.out.println(("Hello, " + "World") + sb.toString());
    System.out.println(sb./* 1 */toString()/* 2 */ + 42);
      /* 1 */
      System.out.println(sb/* 2 */ + "Hello, World");

  }
  void builder(StringBuilder sb) {
    String s1 = sb.toString();
      /* 1 */
      String s2 = sb/* 2 */.substring(1);
      /* 1 */
      String s3 = sb/* 2 */.substring(1, 4);
      /* 1 */
      String s4 = sb/* 2 */.substring(1, 4);
      /* 1 */
      int s5 = sb/* 2 */.substring(1, 4).length();

      /* 1 */
      String s6 = sb.append(sb)/* 2 */.substring(1);
      /* 1 */
      String s7 = (sb/* 2 */ + sb./* 3 */toString()/* 4 */).substring(1);

      /* 1 */
      System.out.println(sb/* 2 */);
      /* 1 */
      System.out.println(sb/* 2 */.substring(1));
      /* 1 */
      System.out.println(sb/* 2 */.substring(1, 3));
      /* 1 */
      System.out.println(sb/* 2 */.substring(1, 3).length());
    System.out.println(sb.substring(1, 3));
    System.out.println(sb.substring(1, 3).length());

    f(sb);
    f(sb.toString());
  }
  void f(StringBuilder s) {}
  void f(String s) {}
}
