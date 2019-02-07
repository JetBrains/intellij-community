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
      @NotNull Object @Nullable [] arr1 = new Object[]{<warning descr="'null' is stored to an array of @NotNull elements">null</warning>, new Object(), Math.random() > 0.5 ? new Object() : <warning descr="'null' is stored to an array of @NotNull elements">null</warning>, <warning descr="Expression 'foo()' might evaluate to null but is stored to an array of @NotNull elements">foo()</warning>};
      @NotNull Object @Nullable [] arr2 = {<warning descr="'null' is stored to an array of @NotNull elements">null</warning>, new Object(), Math.random() > 0.5 ? new Object() : <warning descr="'null' is stored to an array of @NotNull elements">null</warning>, <warning descr="Expression 'foo()' might evaluate to null but is stored to an array of @NotNull elements">foo()</warning>};
      return new Object[]{<warning descr="'null' is stored to an array of @NotNull elements">null</warning>, new Object(), Math.random() > 0.5 ? new Object() : <warning descr="'null' is stored to an array of @NotNull elements">null</warning>, <warning descr="Expression 'foo()' might evaluate to null but is stored to an array of @NotNull elements">foo()</warning>};
    }
    return new @NotNull Object @Nullable []{<warning descr="'null' is stored to an array of @NotNull elements">null</warning>, new Object(), Math.random() > 0.5 ? new Object() : <warning descr="'null' is stored to an array of @NotNull elements">null</warning>, <warning descr="Expression 'foo()' might evaluate to null but is stored to an array of @NotNull elements">foo()</warning>};
  }

  void test() {
    @NotNull Object @Nullable [] array = getNullableArrayOfNotNullObjects();
    assert array != null;
    array[0] = <warning descr="'null' is stored to an array of @NotNull elements">null</warning>;
    array[1] = Math.random() > 0.5 ? <warning descr="'null' is stored to an array of @NotNull elements">null</warning> : "foo";
    array[3] = <warning descr="Expression 'foo()' might evaluate to null but is stored to an array of @NotNull elements">foo()</warning>;
  }
  
  native @Nullable Object foo();
}