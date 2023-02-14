
class Main {
  private String s = "";

  void g(Object o) {
    switch (o) {
      case Integer i && o instanceof String s:
        System.out.println(<caret>s);
    };
  }
}