import java.lang.annotation.Documented;
class Text { Foo foo; }
@Bar(Baz.CONST, value = {Baz.CONST}) class Foo {}
@Documented @interface Bar {}
class Baz {
  static int CONST;
}