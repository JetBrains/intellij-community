// "Replace 'a1' with pattern variable" "true"
package pkg;

import java.util.Objects;

class A<T extends CharSequence> {
  private T t;

  @Override
  public final boolean equals(Object o) {
    if (!(o instanceof A<?> a1)) return false;

      return Objects.equals(t, a1.t);
  }
}

