package org.assertj.core.api;

import org.jetbrains.annotations.Nullable;
import java.util.Optional;
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

  private native Optional<String> getOptional();

  public void testAs() {
    Optional<String> id = getOptional();
    if (id.isPresent()) {}
    Assertions.assertThat(id).as("Asserting id not empty.").isNotEmpty();
    if (<warning descr="Condition 'id.isPresent()' is always 'true'">id.isPresent()</warning>) {}
    id = getOptional();
    Assertions.assertThat(id).describedAs("Asserting id present.").isPresent();
    if (<warning descr="Condition 'id.isPresent()' is always 'true'">id.isPresent()</warning>) {}
    id = getOptional();
    Assertions.assertThat(id.isPresent()).as("Alternative asserting id present.").isTrue();
    if (<warning descr="Condition 'id.isPresent()' is always 'true'">id.isPresent()</warning>) {}
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
  public ObjectAssert isPresent() { return this; }
  public ObjectAssert isNotEmpty() { return this; }
  public void isEmpty() {}
}
class AbstractAssert extends Descriptable {}
class Descriptable {
  public native ObjectAssert as(String message, Object... params);
  public native ObjectAssert describedAs(String message, Object... params);
}