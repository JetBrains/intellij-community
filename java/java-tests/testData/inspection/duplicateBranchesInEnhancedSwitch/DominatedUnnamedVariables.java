class C {
  void foo(Object o) {
    switch (o) {
      case Integer _:
        System.out.println("hello");
        break;
      case String _:
        System.out.println("hello3");
        break;
      case CharSequence _:
        System.out.println("hello");
        break;
      default:
        System.out.println("hello2");
        break;
    }
  }
}