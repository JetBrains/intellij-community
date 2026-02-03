class User {
  private String name;

  String name() {
    return name;
  }

  void name(String name) {
    this.name = name;
  }
  
  void abbreviateName() {
    name = name.substring(0, 100);
  }
}
class Use {
    void test(User user) {
        user.<caret>abbreviateName();
    }
}