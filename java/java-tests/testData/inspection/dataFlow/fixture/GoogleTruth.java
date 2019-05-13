import org.jetbrains.annotations.Nullable;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

class Contracts {

  private void checkNotNullValue(@Nullable Object o) {
    assertThat(o).isNotNull();
    System.out.println(o.hashCode());
  }

  private void assumeNotNullValue(@Nullable Object o) {
    assume().that(o).isNotNull();
    System.out.println(o.hashCode());
  }
}