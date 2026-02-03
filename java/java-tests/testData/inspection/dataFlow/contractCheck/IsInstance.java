import org.jetbrains.annotations.Contract;

class Foo {
  @Contract("_, null -> fail")
  static <T, R extends T> boolean canCast(T inst, Class<R> toType) {
    return toType.isInstance(inst);
  }
}
