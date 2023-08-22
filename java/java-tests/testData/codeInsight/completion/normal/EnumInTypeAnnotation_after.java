import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;

class A {
  List<@Anno(value = Anno.MyEnum.foo<caret>) String> l;

}

@Target(ElementType.TYPE_USE)
@interface Anno {
  enum MyEnum {foo,bar}
  MyEnum value();
}