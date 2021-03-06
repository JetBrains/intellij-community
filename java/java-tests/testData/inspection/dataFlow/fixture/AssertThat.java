import org.hamcrest.CoreMatchers;
import org.jetbrains.annotations.Nullable;
import org.assertj.core.api.Assertions;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
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
    assertThat("1", getBooleanWrapper(1), is(true));
    assertThat("2", getBooleanPrimitive(2), is(true));
    <warning descr="The call to 'assertThat' always fails, according to its method contracts">assertThat</warning>("b is not true", <weak_warning descr="Value 'b' is always 'true'">b</weak_warning>, not(is(true)));
  }
  
  private native Boolean getBooleanWrapper(int x);
  private native boolean getBooleanPrimitive(int x);

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

  private void testNotArraySize() {
    String[] things = retrieveThings();
    assertThat(things, not(is(arrayWithSize(2))));
    assertThat(<warning descr="Array access 'things[0]' may produce 'NullPointerException'">things[0]</warning>, is(equalTo("...")));
  }

  void testBoxed(Contracts c) {
    assertThat(c.getSomething(), is(true));
  }

  Boolean getSomething() {
    return true;
  }

  void testOptional() {
    Optional<String> id = obtainOptional();
    Assertions.assertThat(id.isPresent()). isTrue();
    if (<warning descr="Condition 'id.isPresent()' is always 'true'">id.isPresent()</warning>) {}
    id = obtainOptional();
    Assertions.assertThat(id).isNotEmpty();
    if (<warning descr="Condition 'id.isPresent()' is always 'true'">id.isPresent()</warning>) {}
    id = obtainOptional();
    Assertions.assertThat(id).isPresent();
    if (<warning descr="Condition 'id.isPresent()' is always 'true'">id.isPresent()</warning>) {}
    id = obtainOptional();
    Assertions.assertThat(id).isPresent().map(this::convert).isEmpty();
    if (<warning descr="Condition 'id.isPresent()' is always 'true'">id.isPresent()</warning>) {}
  }
  
  native @Nullable String convert(String s);

  native Optional<String> obtainOptional();

  void testArray(int[] array, int[] array2) {
    if (Math.random() > 0.5) {
      Assertions.assertThat(array).isEmpty();
      System.out.println(array[<warning descr="Array index is out of bounds">0</warning>]);
    }
    Assertions.assertThat(array2).isNotEmpty();
    if (<warning descr="Condition 'array2.length == 0' is always 'false'">array2.length == 0</warning>) {}
    Assertions.<warning descr="The call to 'assertThat' always fails, according to its method contracts">assertThat</warning>(array2).isEmpty();
  }

  void testString(String str, String str2) {
    if (Math.random() > 0.5) {
      Assertions.assertThat(str).isEmpty();
      System.out.println(str.<warning descr="The call to 'charAt' always fails as index is out of bounds">charAt</warning>(0));
    }
    Assertions.assertThat(str2).isNotEmpty();
    if (<warning descr="Condition 'str2.length() == 0' is always 'false'">str2.length() == 0</warning>) {}
    Assertions.<warning descr="The call to 'assertThat' always fails, according to its method contracts">assertThat</warning>(str2).isEmpty();
  }

  void testList(List<String> list, List<String> list2) {
    if (Math.random() > 0.5) {
      Assertions.assertThat(list).isEmpty();
      System.out.println(list.<warning descr="The call to 'get' always fails as index is out of bounds">get</warning>(0));
    }
    Assertions.assertThat(list2).isNotEmpty();
    if (<warning descr="Condition 'list2.size() == 0' is always 'false'">list2.size() == 0</warning>) {}
    Assertions.<warning descr="The call to 'assertThat' always fails, according to its method contracts">assertThat</warning>(list2).isEmpty();
  }
  
  void testAtomicBoolean() {
    AtomicBoolean b = new AtomicBoolean(false);
    Runnable r = () -> b.set(true);
    r.run();
    Assertions.assertThat(b).isTrue();
  }
}