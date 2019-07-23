import org.jetbrains.annotations.NotNull;

class HelperClazz {

  abstract class ClassA {

    <warning descr="Primitive type members cannot be annotated">@NotNull</warning>
    abstract void aMethod();

    abstract void bMethod(<warning descr="Primitive type members cannot be annotated">@NotNull</warning> int a);
  }

  class ClassB extends ClassA {

    @Override
    void aMethod() {}

    @Override
    void bMethod(int a) {}
  }
}