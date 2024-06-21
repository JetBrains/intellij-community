import org.jetbrains.annotations.NotNull;

class Main111 {

  Main111(<warning descr="Parameter annotated @NotNull should not receive 'null' as an argument">@NotNull</warning> Object o) {

  }

  static class SubClass {
    SubClass(<warning descr="Parameter annotated @NotNull should not receive 'null' as an argument">@NotNull</warning> Object o) {

    }
  }

  static class SubClass2 {
    SubClass2(<warning descr="Parameter annotated @NotNull should not receive 'null' as an argument">@NotNull</warning> Object o) {

    }
  }
  
  // IDEA-355111
  record MyRecord(<warning descr="Parameter annotated @NotNull should not receive 'null' as an argument">@NotNull</warning> Object o,
                  @NotNull Object o2) {}

  record MyRecord2(<warning descr="Parameter annotated @NotNull should not receive 'null' as an argument">@NotNull</warning> Object o,
                  @NotNull Object o2) {
    MyRecord2 {
    }
  }

  record MyRecord3(@NotNull Object o, @NotNull Object o2) {
    MyRecord3(<warning descr="Parameter annotated @NotNull should not receive 'null' as an argument">@NotNull</warning> Object o, @NotNull Object o2) {
      this.o = o;
      this.o2 = o2;
    }
  }

  static void main() {
    new Main111(null);
    new Main111.SubClass(null);
    new SubClass2(null);
    new MyRecord(null, "");
    new MyRecord2(null, "");
    new MyRecord3(null, "");

    new ParamerizedRunnable(null) {
      @Override
      void run() {

      }
    };
  }

  abstract static class ParamerizedRunnable {
    private Object parameter;

    public ParamerizedRunnable(<warning descr="Parameter annotated @NotNull should not receive 'null' as an argument">@NotNull</warning> Object parameter) {
      this.parameter = parameter;
    }

    abstract void run();
  }
}