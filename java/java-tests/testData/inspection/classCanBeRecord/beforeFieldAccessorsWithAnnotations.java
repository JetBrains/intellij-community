// "Convert to record class" "true"
// no "true-preview" above because of IDEA-369873
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

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.SOURCE)
@interface Parameter {
}

@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
@interface FieldAndMethod {
  int value();
}

interface IR<T> {
    T getA();
}

class <caret>R<T extends Number> implements IR<T> {
    final T a;
    @Field(value = 1) @Field(value = 2) final int b;
    @FieldAndMethod(1) final int c;
    @FieldAndMethod(3) final int d;
    final int e;

    R(@Parameter T a, int b, int c, int d, int e) {
      this.a = a;
      this.b = b;
      this.c = c;
      this.d = d;
      this.e = e;
    }

    @Override
    @Method
    public T getA() {
      return a;
    }

    @Field(value = 2)
    int b() {
      return b;
    }

    int getC() {
      return c;
    }

    @FieldAndMethod(3)
    int d() {
      return d;
    }

    @FieldAndMethod(4)
    int getE() {
      return e;
    }
}
