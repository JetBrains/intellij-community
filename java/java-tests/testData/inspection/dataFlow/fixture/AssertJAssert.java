package org.assertj.core.api;

import org.jetbrains.annotations.Nullable;
import java.util.stream.*;

class Sample {
  public void aMethod2(Integer someValue) {
    Object object = methodWhichCanReturnNull(someValue);

    Assertions.assertThat(object).isNotNull();
    if (<warning descr="Condition 'object == null' is always 'false'">object == null</warning>) {}
  }


  @Nullable
  private Object methodWhichCanReturnNull(Integer someValue) {
    return someValue;
  }

  private Boolean getB() {
    return Math.random() > 0.5 ? Boolean.TRUE : Boolean.FALSE;
  }

  void foo() {
    Boolean success = getB();
    Assertions.assertThat(success).isTrue();
  }

  void unexpectedInspection0() {
    Stream<String> stream = Stream.of("hello", "world");
    Assertions.assertThat(stream).isEmpty();
  }

  void unexpectedInspection1() {
    Stream<String> stream = Stream.empty();
    Assertions.assertThat(stream).isEmpty();
  }
}
class Assertions {
  public static ObjectAssert assertThat(Object actual) {
    return new ObjectAssert(actual);
  }
}
class ObjectAssert extends AbstractAssert {
  ObjectAssert(Object obj) {}
  
  public ObjectAssert isNotNull() {
    return this;
  }
  public ObjectAssert isTrue() { return this; }
  public void isEmpty() {}
}
class AbstractAssert {}