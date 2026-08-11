package org.assertj.core.api;

import org.jetbrains.annotations.Nullable;
import java.util.function.Function;

class Sample {
  native static @Nullable Object getObject();

  void test() {
    Assertions.assertThat(getObject())
      .isNotNull()
      .extracting(Object::toString)
      .isEqualTo("");
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

  public <V> ObjectAssert<V> extracting(Function<? super T, V> extractor) {
    return new ObjectAssert<>(null);
  }

  public ObjectAssert<T> isEqualTo(Object expected) {
    return this;
  }
}

class AbstractAssert {}
