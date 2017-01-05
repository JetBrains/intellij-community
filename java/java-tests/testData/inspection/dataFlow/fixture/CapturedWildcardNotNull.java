import org.jetbrains.annotations.NotNull;

abstract class Foo {
  @NotNull
  abstract <T> T get(@NotNull Class<? extends T> type);

  void foo(Field field) {
    Object value = get(field.getType());
    if (<warning descr="Condition 'value != null' is always 'true'">value != null</warning>) {

    }
  }
}

interface Field {
  Class<?> getType();
}
