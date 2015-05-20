import org.jetbrains.annotations.Nullable;

import static com.google.common.truth.Truth.assertThat;

class Contracts {

  private void checkNotNullValue(@Nullable Object o) {
    assertThat(o).isNotNull();
    System.out.println(o.hashCode());
  }

}