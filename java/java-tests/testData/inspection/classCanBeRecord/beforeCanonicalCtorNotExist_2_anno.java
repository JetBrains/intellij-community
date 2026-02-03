// "Convert to record class" "true-preview"

import java.lang.annotation.*;

@Target({ElementType.FIELD})
@interface MultiField {
  Field[] value();
}

@Target({ElementType.FIELD})
@Repeatable(MultiField.class)
@interface Field {
  int value() default 1;
}

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
@interface Method {
}

@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
@interface FieldAndMethod {
  int value();
}

class <caret>R<T extends Number> {
  final T a;
  @Field(value = 1) @Field(value = 2) final int b;
  @FieldAndMethod(1) final int c;

  @Method
  public T getA(){
    return a;
  }
}
