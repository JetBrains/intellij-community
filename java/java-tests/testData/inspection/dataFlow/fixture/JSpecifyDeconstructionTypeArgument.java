import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
abstract class JSpecifyDeconstructionTypeArgument {
  interface Foo<T extends @Nullable Object> {
    record FooInner<T extends @Nullable Object>(T value) implements Foo<T> {}

    record NullableInner<T extends @Nullable Object>(@Nullable T value) implements Foo<T> {}
  }

  record Box<T extends @Nullable Object>(Foo<T> foo) {}

  sealed interface Bar {
    record BarInner() implements Bar {}
  }

  abstract Foo<Bar> makeFooBar();

  // The pattern instantiates T with the non-null Bar, so bar is not null.
  void unnamedPattern() {
    Foo<Bar> foo = makeFooBar();
    if (foo instanceof Foo.FooInner<Bar>(Bar bar)) {
      switch (bar) {
        case Bar.BarInner ignored -> {}
      }
    }
  }

  void unnamedPatternNullCheck() {
    Foo<Bar> foo = makeFooBar();
    if (foo instanceof Foo.FooInner<Bar>(Bar bar)) {
      if (<warning descr="Condition 'bar == null' is always 'false'">bar == null</warning>) {}
    }
  }

  // The component itself is @Nullable, so the type argument does not make it non-null.
  void nullableComponent() {
    Foo<Bar> foo = makeFooBar();
    if (foo instanceof Foo.NullableInner<Bar>(Bar bar)) {
      switch (<warning descr="Dereference of 'bar' may produce 'NullPointerException'">bar</warning>) {
        case Bar.BarInner ignored -> {}
      }
    }
  }

  void varComponent() {
    Foo<Bar> foo = makeFooBar();
    if (foo instanceof Foo.FooInner<Bar>(var bar)) {
      switch (bar) {
        case Bar.BarInner ignored -> {}
      }
    }
  }

  void nullableVarComponent() {
    Foo<Bar> foo = makeFooBar();
    if (foo instanceof Foo.NullableInner<Bar>(var bar)) {
      switch (<warning descr="Dereference of 'bar' may produce 'NullPointerException'">bar</warning>) {
        case Bar.BarInner ignored -> {}
      }
    }
  }

  void typeTestPattern() {
    Foo<Bar> foo = makeFooBar();
    if (foo instanceof Foo.FooInner<Bar> fi) {
      switch (fi.value()) {
        case Bar.BarInner ignored -> {}
      }
    }
  }

  void nestedPattern(Box<Bar> box) {
    if (box instanceof Box<Bar>(Foo.FooInner<Bar>(Bar bar))) {
      switch (bar) {
        case Bar.BarInner ignored -> {}
      }
    }
  }

  void typeTestPatternNullCheck() {
    Foo<Bar> foo = makeFooBar();
    if (foo instanceof Foo.FooInner<Bar> fi) {
      if (<warning descr="Condition 'fi.value() == null' is always 'false'">fi.value() == null</warning>) {}
    }
  }

  void nestedPatternNullCheck(Box<Bar> box) {
    if (box instanceof Box<Bar>(Foo.FooInner<Bar>(Bar bar))) {
      if (<warning descr="Condition 'bar == null' is always 'false'">bar == null</warning>) {}
    }
  }

  // The context type says the component is nullable, so the written non-null type argument does not apply.
  void nullableContextTypeTest(Foo.FooInner<@Nullable Bar> foo) {
    if (<warning descr="Condition 'foo instanceof Foo.FooInner<Bar> fi' is always 'true'">foo instanceof Foo.FooInner<Bar> fi</warning>) {
      fi.value().<warning descr="Method invocation 'hashCode' may produce 'NullPointerException'">hashCode</warning>();
    }
  }

  // A nullability written on a pattern type is ignored, so the not-null context type wins.
  void writtenNullableIgnored() {
    Foo<Bar> foo = makeFooBar();
    if (foo instanceof Foo.FooInner<@Nullable Bar> fi) {
      if (<warning descr="Condition 'fi.value() == null' is always 'false'">fi.value() == null</warning>) {}
    }
  }

  // The pattern cannot check the nullness of the type argument, so the nullable context type stays.
  void nullableContextDeconstruction(Foo.FooInner<@Nullable Bar> foo) {
    if (foo.value() == null) {
      if (<warning descr="Condition 'foo instanceof Foo.FooInner<Bar>(Bar bar)' is always 'true'">foo instanceof Foo.FooInner<Bar>(Bar bar)</warning>) {
        System.out.println(bar.<warning descr="Method invocation 'hashCode' will produce 'NullPointerException'">hashCode</warning>());
      }
    }
  }
}
