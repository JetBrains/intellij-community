// "Convert to record class" "false"
// Reason: not implemented

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
@interface Field1 {
}

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
@interface Field2 {
}

class Main<caret> {
  @Field1 @Field2 final int a;
  final int b;

  Main(int a, int b) {
    this.a = a + b;
    this.b = b;
  }
}
