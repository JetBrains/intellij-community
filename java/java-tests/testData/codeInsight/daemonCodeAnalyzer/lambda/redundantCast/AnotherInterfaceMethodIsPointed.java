import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

interface IoFunction<T> extends Consumer<T> {
  @Override
  default void accept(T t) {}

  void acceptX(T t) throws IOException;
}

interface IFunction<T> extends Consumer<T> {
  void accept(T t);
}

interface IIFunction<T> extends Consumer<T> {}

class Test {
  public static void main(String[] args) {
    List<String> strings = Arrays.asList("a", "b", "c");
    strings.forEach((IoFunction<String>) arg -> {throw new IOException();});
    strings.forEach((IFunction<String>) arg -> {});
    strings.forEach((IIFunction<String>) arg -> {});
    strings.forEach((<warning descr="Casting 'arg -> {...}' to 'Consumer<String>' is redundant">Consumer<String></warning>) arg -> {});
  }
}