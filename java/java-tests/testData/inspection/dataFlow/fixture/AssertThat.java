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

  private void checkIsNotNullValue(@Nullable Object o) {
    assertThat(o, CoreMatchers.is(CoreMatchers.<Object>notNullValue()));
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

  private void checkTrue(boolean b) {
    assertThat("b is true", b, is(true));
    if(<warning descr="Condition 'b' is always 'true'">b</warning>) {
      System.out.println("always");
    }
    <warning descr="The call to 'assertThat' always fails, according to its method contracts">assertThat</warning>("b is not true", <weak_warning descr="Value 'b' is always 'true'">b</weak_warning>, not(is(true)));
  }

  private void checkFalse(boolean b) {
    assertThat("b is false", b, is(equalTo(false)));
    if(<warning descr="Condition 'b' is always 'false'">b</warning>) {
      System.out.println("never");
    }
  }

  private void testArraySize() {
    String[] things = retrieveThings();
    assertThat(things, is(arrayWithSize(1)));
    assertThat(things[0], is(equalTo("...")));
  }

  @Nullable
  private static native String[] retrieveThings();
}