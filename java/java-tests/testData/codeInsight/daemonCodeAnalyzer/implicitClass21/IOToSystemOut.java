public class ImplicitClassToExplicit {
  void main() {
    <error descr="Cannot resolve symbol 'IO'">IO<caret></error>.println("Hello World!");
  }
}