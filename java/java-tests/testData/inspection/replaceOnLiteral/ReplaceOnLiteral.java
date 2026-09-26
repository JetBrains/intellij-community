public class ReplaceOnLiteral {
  private static final String PATTERN = "$a$";

  void test(String s) {
    "a".<warning descr="Replacement operation has no effect">replace("b", "c")</warning>;
    "a".<warning descr="Replacement operation has no effect">replace('b', 'c')</warning>;
    "abc".replace('a', 'b');
    "abc".<warning descr="Replacement operation has no effect">replaceFirst("^b", "c")</warning>;
    "abc".replaceFirst("^a", "c");
    "abc".<warning descr="Replacement operation has no effect">replaceAll("^b", "c")</warning>;
    "abc".replaceAll("^a", "c");
    "abc".<warning descr="Replacement operation has no effect">replaceAll(PATTERN, s)</warning>;
    "abc".<warning descr="Replacement operation has no effect">replace(PATTERN, s)</warning>;

    "a".<warning descr="Replacement operation has no effect">replace("b" + s, "")</warning>;
    "a".replace("a" + s, "");
    "a".replace('a' + s, "");
    "a".<warning descr="Replacement operation has no effect">replace('b' + s, "")</warning>;
    "a".replace(123 + s, "");
    "a".<warning descr="Replacement operation has no effect">replace((s + ('x') + s), "c")</warning>;
    "a".<warning descr="Replacement operation has no effect">replace(PATTERN+s, "")</warning>;

    "a".replaceFirst("x" + s, "");
  }
}
