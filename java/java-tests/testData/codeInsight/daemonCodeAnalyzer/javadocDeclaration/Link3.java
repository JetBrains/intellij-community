/// [java.lang.String]
/// [foo]
/// {@link foo}
/// [<error descr="Cannot resolve symbol 'bar'">bar</error>]
/// {@link <error descr="Cannot resolve symbol 'bar'">bar</error>}
class Test {
  public void foo(Object bar) {}

}