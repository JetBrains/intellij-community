import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class UnboundedWildcardReturnOverride {
  interface Producer {
    Container<? extends Object> produce();
  }

  interface SpecializedProducer extends Producer {
    @Override
    <warning descr="Overriding a class with nullable type arguments when a class with not-null type arguments is expected">Container<?></warning> produce();
  }

  interface Container<T extends @Nullable Object> {}
}
