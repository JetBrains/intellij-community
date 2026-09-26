import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class EnumExample {
  static @interface MyAnnotation {
    MyEnum enumValue();
  }

  static enum MyEnum { E }

  static final MyEnum E = MyEnum.E;

  @MyAnnotation(enumValue = <error descr="Attribute value must be an enum constant">E</error>)
  void method() {}
}
@Target((ElementType.LOCAL_VARIABLE))
@interface FlashBulb {
}

@Target(value = {(ElementType.LOCAL_VARIABLE)})
@interface TulipBulb {
}