// "Fix all 'Redundant String operation' problems in file" "true"

class StringBuilderToString {
  StringBuilderToString() {
    String s1 = new StringBuilder().toString();
    String s2 = new StringBuilder()./* 1 */<caret>toString()/* 2 */.substring(1);
    String s3 = new StringBuilder()./* 1 */toString()/* 2 */.substring(1, 4);
    String s4 = new StringBuilder()./* 1 */toString()/* 2 */.substring(1, 4);
    int s5 = new StringBuilder()./* 1 */toString()/* 2 */.substring(1, 4).length();

    System.out.println(new StringBuilder()./* 1 */toString()/* 2 */);
    System.out.println(new StringBuilder()./* 1 */toString()/* 2 */.substring(1));
    System.out.println(new StringBuilder()./* 1 */toString()/* 2 */.substring(1, 3));
    System.out.println(new StringBuilder()./* 1 */toString()/* 2 */.substring(1, 3).length());
    System.out.println(new StringBuilder().substring(1, 3));
    System.out.println(new StringBuilder().substring(1, 3).length());

    StringBuilder sb = new StringBuilder();
    String s6 = sb./* 1 */toString()/* 2 */ + sb. /* 3 */toString()/* 4 */;
    String s7 = sb./* 1 */toString()/* 2 */ + sb./* 3 */toString()/* 4 */;
    String s8 = "Hello, World" + sb.toString();
    String s8 = ("Hello, " + "World") + sb.toString();
    String s9 = sb./* 1 */toString()/* 2 */ + 42;
    String s10 = sb./* 1 */toString()/* 2 */ + "Hello, World";
    String s11 = sb./* 1 */toString()/* 2 */ + sb./* 3 */toString()/* 4 */ + sb./* 5 */toString()/* 6 */;

    System.out.println(sb./* 1 */toString()/* 2 */ + sb. /* 3 */toString()/* 4 */);
    System.out.println(sb./* 1 */toString()/* 2 */ + sb./* 3 */toString()/* 4 */);
    System.out.println(sb./* 1 */toString()/* 2 */ + sb./* 3 */toString()/* 4 */ + sb./* 5 */toString()/* 6 */);
    System.out.println("Hello, World" + sb.toString());
    System.out.println(("Hello, " + "World") + sb.toString());
    System.out.println(sb./* 1 */toString()/* 2 */ + 42);
    System.out.println(sb./* 1 */toString()/* 2 */ + "Hello, World");

  }
  void builder(StringBuilder sb) {
    String s1 = sb.toString();
    String s2 = sb./* 1 */toString()/* 2 */.substring(1);
    String s3 = sb./* 1 */toString()/* 2 */.substring(1, 4);
    String s4 = sb./* 1 */toString()/* 2 */.substring(1, 4);
    int s5 = sb./* 1 */toString()/* 2 */.substring(1, 4).length();

    String s6 = sb.append(sb)./* 1 */toString()/* 2 */.substring(1);
    String s7 = (sb./* 1 */toString()/* 2 */ + sb./* 3 */toString()/* 4 */).substring(1);

    System.out.println(sb./* 1 */toString()/* 2 */);
    System.out.println(sb./* 1 */toString()/* 2 */.substring(1));
    System.out.println(sb./* 1 */toString()/* 2 */.substring(1, 3));
    System.out.println(sb./* 1 */toString()/* 2 */.substring(1, 3).length());
    System.out.println(sb.substring(1, 3));
    System.out.println(sb.substring(1, 3).length());

    f(sb);
    f(sb.toString());
  }
  void f(StringBuilder s) {}
  void f(String s) {}
}
