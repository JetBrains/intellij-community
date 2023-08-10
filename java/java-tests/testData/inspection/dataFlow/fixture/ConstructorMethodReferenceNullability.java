import org.jetbrains.annotations.*;

public class ConstructorMethodReferenceNullability {

  public static void bar() {
    foo(SomeClass::new);
    fooB(SomeClassB::new);
    fooC(SomeClassC::new);
    fooD(<warning descr="Method reference argument might be null">SomeClassD::new</warning>);
    fooE(SomeClassE::new);
  }

  public static void foo(@NotNull Factory factory) {
    factory.create(new Object(), new Object());
  }
  public static void fooB(@NotNull FactoryB factory) {
    factory.create(new Object());
  }
  public static void fooC(@NotNull FactoryC factory) {
    factory.create(new Object(), new Object());
  }

  public static void fooD(@NotNull FactoryD factory) {
    factory.create(null);
  }

  public static void fooE(@NotNull FactoryE factory) {
    factory.create(new Object());
  }

  @FunctionalInterface
  public static interface Factory {
    @NotNull Object create(@NotNull Object notNull, @Nullable Object isNull);
  }
  public static class SomeClass {
    public SomeClass(@NotNull Object anObject, @Nullable Object ignored) {}
  }

  @FunctionalInterface
  public static interface FactoryB {
    @NotNull Object create(@Nullable Object isNull);
  }
  public static class SomeClassB {
    public SomeClassB(@Nullable Object ignored) {}
  }

  @FunctionalInterface
  public static interface FactoryC {
    @NotNull Object create(@NotNull Object notNull, @NotNull Object isNull);
  }
  public static class SomeClassC {
    public SomeClassC(@NotNull Object anObject, @NotNull Object ignored) {}
  }

  @FunctionalInterface
  public static interface FactoryD {
    @NotNull Object create(@Nullable Object isNull);
  }
  public static class SomeClassD {
    public SomeClassD(@NotNull Object ignored) {}
  }

  @FunctionalInterface
  public static interface FactoryE {
    @NotNull Object create(@NotNull Object isNull);
  }
  public static class SomeClassE {
    public SomeClassE(@Nullable Object ignored) {}
  }


}