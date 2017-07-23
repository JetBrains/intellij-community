import java.util.function.Consumer;

class Main {

  private static void test() {
    extendsConsumer((String s) -> {});
  }

  private static <T> void extendsConsumer(ExtendsConsumer<? super T> c) { }

  interface ExtendsConsumer<T> extends Consumer<T> { }

}