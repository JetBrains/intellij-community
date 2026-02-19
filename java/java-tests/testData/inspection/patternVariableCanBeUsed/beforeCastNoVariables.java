// "Replace cast expressions with pattern variable" "false"
package pkg;

import java.util.Objects;

class A<T extends CharSequence> {
  private T t;

  @Override
  public final boolean equals(Object o) {
    if (!(o instanceof A)) return false;

    final A<?> a1 = (A<?>) o<caret><?>;
    return Objects.equals(t, a1.t);
  }
}

