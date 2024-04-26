// "Merge with 'case Integer _'" "true-preview"
class C {
  enum T {A, B,}

  void foo(Object o) {
    switch (o) {
      case Integer _ when o.hashCode()==1:
      case T.A:
        System.out.println("1");
        break;
      case String _:
        System.out.<caret>println("1");
        break;
      default:
        System.out.println("3");
    }
  }
}