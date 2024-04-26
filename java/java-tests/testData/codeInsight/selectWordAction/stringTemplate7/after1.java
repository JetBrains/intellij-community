class Test {
  void test() {
    int i = 1;
    String sql = <selection>STR."""
                select * from customer \{i}
                """<caret></selection>;
  }
}