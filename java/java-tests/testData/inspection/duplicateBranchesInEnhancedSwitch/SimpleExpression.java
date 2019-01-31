class C {
  void foo(int n) {
    String string = switch (n) {
        case 1 -> bar("A");
        case 2 -> bar("B");
        case 3 -> <weak_warning descr="Duplicate result expression in 'switch' expression">bar("A");</weak_warning>
        default -> "";
      };
  }
  String bar(String s){return s;}
}