import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

class CovariantReturn {
  public interface Base {
    @Nullable
    String foo();

    Object baz();
  }

  public interface Derived extends Base {
    @Override
    @NotNull
    String foo();

    String baz();
  }

  public interface BaseGeneric<T> {
    T get(@Nullable T t);
  }

  public static class DerivedGeneric implements BaseGeneric<Integer> {
    @Override
    public Integer get(Integer integer) {
      return integer == null ? 0 : integer+1;
    }
  }

  public static class DerivedGenericImpl extends DerivedGeneric {

  }

  public static boolean bar(@NotNull String s) {
    return true;
  }

  // IDEA-178773 Can't get to green code without suppressing an inspection in case of restricted nullability in overridden method
  public static boolean test1(Base base) {
    return base instanceof Derived && bar(base.foo());
  }

  public static boolean test2(Base base) {
    return base instanceof Derived || bar(<warning descr="Argument 'base.foo()' might be null">base.foo()</warning>);
  }

  boolean testType(Base base) {
    return base instanceof Derived && <warning descr="Condition 'base.baz() instanceof String' is redundant and can be replaced with '!= null'">base.baz() instanceof String</warning>;
  }

  boolean testTypeGeneric(BaseGeneric<?> base) {
    return base instanceof DerivedGenericImpl && <warning descr="Condition 'base.get(null) instanceof Integer' is redundant and can be replaced with '!= null'">base.get(null) instanceof Integer</warning>;
  }
}