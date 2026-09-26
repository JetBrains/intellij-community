import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@NullMarked
class JSpecifyUnspecifiedTypeArgumentInstantiatedWithNullable {
  interface Cache<V extends @Nullable Object> {}

  interface CacheFactory<V extends @Nullable Object> {
    Cache<@NullnessUnspecified V> createCache();
  }

  class Registry {
    Cache<? extends Object> withStrictValues(CacheFactory<Object> factory) {
      return factory.createCache();
    }

    Cache<? extends Object> withUnspecifiedValues(CacheFactory<@NullnessUnspecified Object> factory) {
      return factory.createCache();
    }

    Cache<? extends Object> withNullableValues(CacheFactory<@Nullable Object> factory) {
      return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected">factory.createCache()</warning>;
    }

    Cache<? extends Object> withUnspecifiedValueBound(CacheFactory<? extends @NullnessUnspecified Object> factory) {
      return factory.createCache();
    }

    Cache<? extends Object> withNullableValueBound(CacheFactory<? extends @Nullable Object> factory) {
      return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected">factory.createCache()</warning>;
    }
  }
}
