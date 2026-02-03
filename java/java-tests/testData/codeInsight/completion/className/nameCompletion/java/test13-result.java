package aaa;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@interface MyObjectType {}

class A {
  MyObjectType<caret>
}