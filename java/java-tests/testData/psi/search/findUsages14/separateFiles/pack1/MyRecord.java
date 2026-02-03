package pack1;

public record MyRecord (String s) {
  void foo() {
    String s = s();
  }
}

