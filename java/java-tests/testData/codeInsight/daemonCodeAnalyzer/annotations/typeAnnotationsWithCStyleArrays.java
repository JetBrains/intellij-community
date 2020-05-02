import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@interface MyAnnoWithDefaults {
  int foo() @Anno[] default {};
  int @Anno [] foo1()  default {};
  String @Anno [] foo2()  default {""};
  String foo3() @Anno[] default {""};
  @Anno String foo4() default "";
}

@Target({ElementType.TYPE, ElementType.TYPE_USE})
@interface Anno {}