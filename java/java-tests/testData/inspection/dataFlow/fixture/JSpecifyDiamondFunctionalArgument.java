import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Test {
  record SomeRecord<I extends @Nullable String>(Supplier<I> someSupplier) {}
  record FieldRecord<I extends @Nullable String>(I someField) {}
  record SomeObject<I extends @Nullable Object>(Supplier<I> someSupplier) {}

  void nullableTarget() {
    SomeRecord<@Nullable String> lambda = new SomeRecord<>(() -> null);
    SomeRecord<@Nullable String> methodRef = new SomeRecord<>(this::someStringOrNull);
    SomeRecord<@Nullable String> explicitLambda = new SomeRecord<@Nullable String>(() -> null);
    SomeRecord<@Nullable String> explicitMethodRef = new SomeRecord<@Nullable String>(this::someStringOrNull);
    SomeObject<@Nullable String> lambda2 = new SomeObject<>(() -> null);
    SomeObject<@Nullable String> methodRef2 = new SomeObject<>(this::someStringOrNull);
    SomeObject<@Nullable String> explicitLambda2 = new SomeObject<@Nullable String>(() -> null);
    SomeObject<@Nullable String> explicitMethodRef2 = new SomeObject<@Nullable String>(this::someStringOrNull);
  }

  static <I extends @Nullable String> SomeRecord<I> create(Supplier<I> s) { return new SomeRecord<I>(s); }

  void notNullTarget() {
    SomeRecord<String> factoryMethodRef = create(this::someStringOrNull);
    SomeRecord<String> factoryLambda = create(() -> <warning descr="'null' is returned by the method declared as @NullMarked">null</warning>);
    SomeRecord<String> lambda = new SomeRecord<>(() -> <warning descr="'null' is returned by the method declared as @NullMarked">null</warning>);
    SomeRecord<String> methodRef = new SomeRecord<>(this::someStringOrNull);
    SomeRecord<String> explicitMethodRef = new SomeRecord<String>(<warning descr="Function may return null, but it's not allowed here">this::someStringOrNull</warning>);
    SomeObject<String> lambda2 = new SomeObject<>(() -> <warning descr="'null' is returned by the method declared as @NullMarked">null</warning>);
    SomeObject<String> methodRef2 = new SomeObject<>(this::someStringOrNull);
    SomeObject<String> explicitMethodRef2 = new SomeObject<String>(<warning descr="Function may return null, but it's not allowed here">this::someStringOrNull</warning>);
    FieldRecord<String> field = new FieldRecord<>(someStringOrNull());
    FieldRecord<String> fieldNull = new FieldRecord<>(<warning descr="Passing 'null' argument to parameter annotated as non-null">null</warning>);
  }

  @Nullable String someStringOrNull() {
    return null;
  }
}
