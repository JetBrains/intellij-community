@SuppressWarnings(ThisClass.<error descr="'FOO' has private access in 'ThisClass'">FOO</error>)
@A(@B(ThisClass.<error descr="'FOO' has private access in 'ThisClass'">FOO</error>))
public class ThisClass {
  private static final String FOO = "foo";
}


@interface A {
  B value();
}

@interface B {
  String value();
}