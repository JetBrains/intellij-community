import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class JSpecifyNullableBoundInstantiatedWithNotNull extends SupWithNullableBound<Object> {
  // `T` is instantiated with a not-null `Object`, so the nullable bound of the super method is not inherited here
  @Override
  public void test(Object arg) {
    super.test(arg);
  }

  @NullMarked
  static class InstantiatedWithNullable extends SupWithNullableBound<@Nullable Object> {
    @Override
    public void test(Object <warning descr="Parameter annotated @NullMarked must not override @Nullable parameter">arg</warning>) {
      super.test(arg);
    }
  }
}
