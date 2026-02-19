// "Fix all 'Unnecessary Unicode escape sequence' problems in file" "true"
class X {
	void test() {
    String s = "abcd";
    String t = """
      \t
""";
  }
}