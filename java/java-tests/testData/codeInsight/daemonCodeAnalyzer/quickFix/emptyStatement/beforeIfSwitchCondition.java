// "Extract side effect" "true-preview"
class Z {

  void z() {
    i<caret>f (switch(0) {
      case 0 -> false;
      case 1 -> {
        System.out.println("oops");
        yield true;
      }
      case 2 -> {
        System.out.println("bar");
        yield Math.random() > 0.5 && new Foo().getBar();
      }
      case 3 -> {
        if(Math.random() > 0.5) yield true;
        else yield false;
      }
      case 4 -> {
        if(Math.random() > 0.5) yield true;
        System.out.println("four");
        yield false;
      }
      default -> "foo"+(new Foo());
    }) {}
  }
}