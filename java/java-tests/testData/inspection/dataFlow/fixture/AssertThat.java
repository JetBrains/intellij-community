import org.hamcrest.CoreMatchers;
import org.jetbrains.annotations.Nullable;
import org.assertj.core.api.Assertions;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

class Contracts {

  private void checkNotNullValue(@Nullable Object o) {
    assertThat(o, CoreMatchers.<Object>notNullValue());
    System.out.println(o.hashCode());
  }

  private void checkNotEqualToNull(@Nullable String test) {
    assertThat("String is null", test, not(equalTo(null)));
    int length = test.length();
  }

  public void checkAssertJ(@Nullable Object object) {
    Assertions.assertThat(object).isNotNull();
    System.out.println(object.toString());
  }

}