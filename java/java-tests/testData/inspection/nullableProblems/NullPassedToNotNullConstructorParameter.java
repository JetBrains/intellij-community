import org.jetbrains.annotations.NotNull;

class Main111 {

  Main111(<warning descr="Parameter annotated @NotNull should not receive null as an argument">@NotNull</warning> Object o) {

  }

  static class SubClass {
    SubClass(<warning descr="Parameter annotated @NotNull should not receive null as an argument">@NotNull</warning> Object o) {

    }
  }

  static class SubClass2 {
    SubClass2(<warning descr="Parameter annotated @NotNull should not receive null as an argument">@NotNull</warning> Object o) {

    }
  }

  static void main() {
    new Main111(null);
    new Main111.SubClass(null);
    new SubClass2(null);

    new ParamerizedRunnable(null) {
      @Override
      void run() {

      }
    };
  }

  abstract static class ParamerizedRunnable {
    private Object parameter;

    public ParamerizedRunnable(<warning descr="Parameter annotated @NotNull should not receive null as an argument"><warning descr="Parameter annotated @NotNull should not receive null as an argument">@NotNull</warning></warning> Object parameter) {
      this.parameter = parameter;
    }

    abstract void run();
  }
}