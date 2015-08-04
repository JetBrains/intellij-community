package foo;

import javax.annotation.*;

enum TestNonnull {
  TEST {
    @Nonnull
    @Override
    public Object testWithAnnotation(@Nonnull Object aObject) {
      if (<warning descr="Condition 'aObject == null' is always 'false'">aObject == null</warning>) {
        return new Object();
      }
      return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }

    @Override
    public Object testWithoutAnnotation(Object aObject) {
      return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }
  };

  public boolean testParameter(TestNonnull aTest) {
    if (<warning descr="Condition 'aTest == null' is always 'false'">aTest == null</warning>) {
      return true;
    }
    return false;
  }

  public Object testReturn() {
    return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
  }

  @Nonnull
  public abstract Object testWithAnnotation(@Nonnull Object aObject);

  public abstract Object testWithoutAnnotation(Object aObject);
}