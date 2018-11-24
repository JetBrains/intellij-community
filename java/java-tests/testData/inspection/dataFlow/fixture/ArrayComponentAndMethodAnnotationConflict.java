import withTypeUse.NotNull;
import withTypeUse.Nullable;

interface Foo {
  @Nullable Object @NotNull [] getNotNullArrayOfNullableObjects();
  @NotNull Object @Nullable [] getNullableArrayOfNotNullObjects();
}

class FooImpl implements Foo {

  @Override
  public @Nullable Object @NotNull [] getNotNullArrayOfNullableObjects() {
    return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
  }

  @Override
  public @NotNull Object @Nullable [] getNullableArrayOfNotNullObjects() {
    if (Math.random() > 0.5) {
      return null;
    }
    else {
      return new Object[]{null, new Object()};
    }
  }
}