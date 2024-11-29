import java.util.Optional;

class Super {
  private int x;

  public void foo(Optional<Sub> opt) {
    if (opt == null) opt = Optional.empty();
    if (opt.isPresent()) {
      // Changing type of `obj` from Super to Sub will cause a compile-time error (field x is private). Hence, don't suggest fix.
      Super obj = opt.get();
      use(obj.x);
    }
  }

  void use(Object obj) {
    System.out.println("Object");
  }
}

class Sub extends Super {}
