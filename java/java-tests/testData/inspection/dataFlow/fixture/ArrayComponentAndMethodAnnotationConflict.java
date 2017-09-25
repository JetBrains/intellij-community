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
    else if (Math.random() > 0.5) {
      @NotNull Object @Nullable [] arr1 = new Object[]{<warning descr="'null' is stored to an array of @NotNull elements">null</warning>, new Object(), <warning descr="Expression 'Math.random() > 0.5 ? new Object() : null' might evaluate to null but is stored to an array of @NotNull elements">Math.random() > 0.5 ? new Object() : null</warning>};
      @NotNull Object @Nullable [] arr2 = {<warning descr="'null' is stored to an array of @NotNull elements">null</warning>, new Object(), <warning descr="Expression 'Math.random() > 0.5 ? new Object() : null' might evaluate to null but is stored to an array of @NotNull elements">Math.random() > 0.5 ? new Object() : null</warning>};
      return new Object[]{<warning descr="'null' is stored to an array of @NotNull elements">null</warning>, new Object(), <warning descr="Expression 'Math.random() > 0.5 ? new Object() : null' might evaluate to null but is stored to an array of @NotNull elements">Math.random() > 0.5 ? new Object() : null</warning>};
    }
    return new @NotNull Object @Nullable []{<warning descr="'null' is stored to an array of @NotNull elements">null</warning>, new Object(), <warning descr="Expression 'Math.random() > 0.5 ? new Object() : null' might evaluate to null but is stored to an array of @NotNull elements">Math.random() > 0.5 ? new Object() : null</warning>};
  }

  void test() {
    @NotNull Object @Nullable [] array = getNullableArrayOfNotNullObjects();
    assert array != null;
    array[0] = <warning descr="'null' is stored to an array of @NotNull elements">null</warning>;
    array[1] = <warning descr="Expression 'Math.random() > 0.5 ? null : \"foo\"' might evaluate to null but is stored to an array of @NotNull elements">Math.random() > 0.5 ? null : "foo"</warning>;
  }
}