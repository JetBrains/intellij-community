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

  public void checkAssertJ(@Nullable Object object, @Nullable Object object2) {
    Assertions.assertThat(object).isNotNull();
    System.out.println(object.toString());

    Assertions.assertThat(object2).describedAs("x").isNotNull();
    System.out.println(object2.toString());
  }

}