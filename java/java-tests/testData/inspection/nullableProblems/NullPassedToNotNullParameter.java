import org.jetbrains.annotations.NotNull;

class Test {
  void someMethod(<warning descr="Parameter annotated @NotNull should not receive null as an argument">@NotNull</warning> Object warn, <warning descr="Parameter annotated @NotNull should not receive null as an argument">@NotNull</warning> Object o2, <warning descr="Parameter annotated @NotNull should not receive null as an argument">@NotNull</warning> Object warn1) {

  }

  static void someStaticMethod(Object notAnnotated, <warning descr="Parameter annotated @NotNull should not receive null as an argument">@NotNull</warning> Object o2, <warning descr="Parameter annotated @NotNull should not receive null as an argument">@NotNull</warning> Object warn2) {

  }

  public static void main(String[] args) {
    someStaticMethod(null, "", "");

    Test.someStaticMethod("", null, null);

    new Test().someMethod("", null, null);

    Test m = new Test();

    m.someMethod(null, "", "");
    m.someMethod(null, "", "");
  }
}
