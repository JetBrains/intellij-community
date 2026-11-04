@interface MyAnno {
  String value() default "";
}
@interface MyAnnoOther {
  String value() default "";
}

public class Foo {
  public void foo(@MyAnno(<caret>) String input) {
  }
}
