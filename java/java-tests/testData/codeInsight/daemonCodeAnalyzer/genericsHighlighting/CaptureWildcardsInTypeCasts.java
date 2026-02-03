import java.util.Collection;

class Foo {
  private void test() {
    Object x = null;
    ((java.util.Collection<?>)x).add<error descr="'add(capture<?>)' in 'java.util.Collection' cannot be applied to '(java.lang.String)'">("")</error>;
  }
}