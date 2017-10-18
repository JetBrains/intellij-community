// "Unwrap 'if' statement" "true"
class X {
  void test(@org.jetbrains.annotations.NotNull String x) {
      {
        String str = x.trim();
        System.out.println(str);
      }

    {{int str;}}
  }
}