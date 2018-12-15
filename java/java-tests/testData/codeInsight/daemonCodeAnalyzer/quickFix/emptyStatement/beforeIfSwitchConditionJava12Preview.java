// "Extract side effect" "true"
class Z {

  void z() {
    i<caret>f (switch(0) {
      case 0 -> false;
      case 1 -> {
        System.out.println("oops");
        break true;
      }
      case 2 -> {
        System.out.println("bar");
        break Math.random() > 0.5 && new Foo().getBar();
      }
      case 3 -> {
        if(Math.random() > 0.5) break true;
        else break false;
      }
      case 4 -> {
        if(Math.random() > 0.5) break true;
        System.out.println("four");
        break false;
      }
      default -> "foo"+(new Foo());
    }) {}
  }
}