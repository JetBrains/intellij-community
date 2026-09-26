import org.checkerframework.checker.tainting.qual.Untainted;

class LocalCheck {


  enum Queries{
    A("1"),
    B("1");

    private final  String code;

    Queries(String number) {
      code = number;
    }

    public String getCode() {
      return code;
    }
  }

  void test() {
    for (Queries value : Queries.values()) {
      sink(value.getCode());
    }
  }

  void sink(@Untainted String clean) {

  }
}
