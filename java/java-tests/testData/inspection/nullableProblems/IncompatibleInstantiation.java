import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.ElementType;
import java.util.function.Supplier;

@NullMarked
class Main {

  public static void main(String[] args) {
    Main.<<warning descr="Non-null type parameter 'T' cannot be instantiated with @Nullable type">@Nullable</warning> Object>fNonNullBound(() -> getNullableObject());
    Main.<<warning descr="Non-null type parameter 'T' cannot be instantiated with @Nullable type">@Nullable</warning> Object>fNonNullBound(Main::getNullableObject);

  }

  static <T extends @Nullable Object> T fNullableBound(Supplier<T> supplier){
    return supplier.get();
  }

  static <T> T fNonNullBound(Supplier<T> supplier){
    return supplier.get();
  }

  static @Nullable Object getNullableObject() {
    return null;
  }

  @NullableScope
  static class NullableScopeClass {
    void test() {
      Main.<<warning descr="Non-null type parameter 'T' cannot be instantiated under @NullableScope">Object</warning>>fNonNullBound(Main::getNullableObject);
    }
  }
}

@javax.annotation.meta.TypeQualifierDefault(ElementType.TYPE_USE) 
@javax.annotation.Nullable
@java.lang.annotation.Target(ElementType.TYPE_USE)
@interface NullableScope {}