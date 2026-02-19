class Test {
  void test() {
    int i = 1;
    String sql = STR.<selection>"""
                select * from customer \{i}
                """<caret></selection>;
  }
}