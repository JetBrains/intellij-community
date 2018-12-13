// "Extract side effect" "true"
class Z {

  void z() {
      switch (0) {
          case 0 -> {
          }
          case 1 -> {
              System.out.println("oops");
          }
          case 2 -> {
              System.out.println("bar");
              if (Math.random() > 0.5) {
                  new Foo().getBar();
              }
          }
          case 3 -> {
              if (Math.random() > 0.5) {
              } else {
              }
          }
          case 4 -> {
              if (Math.random() > 0.5) break;
              System.out.println("four");
          }
          default -> new Foo();
      }
  }
}