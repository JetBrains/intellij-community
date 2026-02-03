import java.util.function.Consumer;

class Test {
  private void main() {
    bar("hello", this::buzz);
  }

  private void bar(String string, Consumer<Integer> o) {
    o.accept(1);
    use(string);
  }

  private void buzz(int  integer) {
    use(integer);
  }

  private void use(Object a) {}
}