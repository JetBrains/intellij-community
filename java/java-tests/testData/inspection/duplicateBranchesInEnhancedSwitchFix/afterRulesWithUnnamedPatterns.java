// "Fix all 'Duplicate branches in 'switch'' problems in file" "true"
class C {
  void foo(Object o) {
    switch (o) {
      case Integer _, String _ -> System.out.println(1); //can be merged
        default -> System.out.println(2);
    }
  }

  void foo2(Object o) {
    switch (o) {
      case Integer _ when o.hashCode() == 1 -> System.out.println(1);
      case String _ -> System.out.println(1);
      default -> System.out.println(2);
    }
  }

  void foo3(Object o) {
    switch (o) {
      case Integer _, String _ when o.hashCode() == 1 -> System.out.println(1); //can be merged
        default -> System.out.println(2);
    }
  }

  void foo4(Object o) {
    switch (o) {
      case Integer _, String _ when o.hashCode() == 1 -> System.out.println(1); //can be merged
      case Number s -> System.out.println(3);
        default -> System.out.println(2);
    }
  }

  void foo5(Object o) {
    switch (o) {
      case Integer _ when o.hashCode() == 1 -> System.out.println(1);
      case Number s -> System.out.println(3);
      case String _ when o.hashCode() == 2 -> System.out.println(1);
      default -> System.out.println(2);
    }
  }

  void foo6(Object o) {
    switch (o) {
      case Integer _ when o.hashCode() == 1 -> System.out.println(1);
      case Number s -> System.out.println(3);
      case String _ -> System.out.println(1);
      default -> System.out.println(2);
    }
  }

  void foo7(Object o) {
    switch (o) {
      case Integer _, String _ -> System.out.println(1); //can be merged
        default -> System.out.println(2);
    }
  }

  void foo8(Object o) {
    switch (o) {
      case Integer _, CharSequence _ when o.hashCode() == 1 -> System.out.println(1); //can be merged
      case Number s -> System.out.println(3);
        default -> System.out.println(2);
    }
  }
}