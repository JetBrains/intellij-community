package org.assertj.core.api;

import org.jetbrains.annotations.*;
import org.jspecify.annotations.NullMarked;
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

  // All methods inside the org.assertj.core.api package are hardcoded to be pure by default
  // See com.intellij.codeInsight.DefaultInferredAnnotationProvider.getHardcodedContractAnnotation
  // We override this, otherwise it spoils the tests, as it's assumed that the same value is always returned
  @Contract(pure = false)
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
  public static <T> ObjectAssert<T> assertThat(T actual) {
    return new ObjectAssert<>(actual);
  }
}
class ObjectAssert<T> extends AbstractAssert {
  ObjectAssert(T obj) {}

  public ObjectAssert<T> isNotNull() {
    return this;
  }
  public ObjectAssert<T> isNull() {
    return this;
  }
  public ObjectAssert<T> isTrue() { return this; }
  public ObjectAssert<T> isPresent() { return this; }
  public ObjectAssert<T> isNotEmpty() { return this; }
  public void isEmpty() {}
}
class AbstractAssert extends Descriptable {}
class Descriptable {
  public native ObjectAssert<?> as(String message, Object... params);
  public native ObjectAssert<?> describedAs(String message, Object... params);
}
@NullMarked
class MarkedAsNull {
  void test() {
    Assertions.assertThat((Object)null).isNull();
  }
}