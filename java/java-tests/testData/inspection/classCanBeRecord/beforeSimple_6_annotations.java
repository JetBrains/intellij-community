// "Convert to record class" "true-preview"

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
@interface Field {
}

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.SOURCE)
@interface Parameter {
}

@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
@interface FieldAndMethod {
  int value();
}

class R<caret> {
  @Field final int x;
  @FieldAndMethod final int y;

  R(@Parameter int x, int y) {
    this.x = x;
    this.y = y;
  }
}
