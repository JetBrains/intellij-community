
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PARAMETER, ElementType.TYPE_USE})
@interface Anno {
  String value() default "";
}

class A {
  @Anno
  String methodA() {};
}

class B {
  private A a;


  <caret>

}