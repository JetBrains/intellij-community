import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

// Direct inheritance tests

class UnmarkedSuper {
  void method(String param) {}
  void methodWithTwoParams(String first, String second) {}
}

@NullMarked
class NullMarkedSubclass extends UnmarkedSuper {
  @Override
  void method(String <warning descr="Parameter annotated @NullMarked should not override non-annotated parameter">param</warning>) {}

  @Override
  void methodWithTwoParams(String <warning descr="Parameter annotated @NullMarked should not override non-annotated parameter">first</warning>,
                           String <warning descr="Parameter annotated @NullMarked should not override non-annotated parameter">second</warning>) {}
}

@NullMarked
class NullMarkedSubclassWithNullable extends UnmarkedSuper {
  @Override
  void method(@Nullable String param) {}
}

@NullMarked
class NullMarkedSuper {
  void method(String param) {}
}

@NullMarked
class NullMarkedSubclass2 extends NullMarkedSuper {
  @Override
  void method(String param) {}
}

// Indirect inheritance test

interface UnmarkedInterface {
  void interfaceMethod(String param);
}

@NullMarked
class NullMarkedBase {
  public void interfaceMethod(String param) {}  // effectively non-null via @NullMarked
}

abstract class IndirectSubclass extends NullMarkedBase implements <warning descr="Non-null parameter 'param' in method 'interfaceMethod' from 'NullMarkedBase' should not override non-annotated parameter from 'UnmarkedInterface'">UnmarkedInterface</warning> {}

// Multi-level inheritance test

class UnmarkedBase2 {
  void chainMethod(String param) {}
}

class UnmarkedIntermediate extends UnmarkedBase2 {
  @Override
  void chainMethod(String param) {}
}

@NullMarked
class NullMarkedChainEnd extends UnmarkedIntermediate {
  @Override
  void chainMethod(String <warning descr="Parameter annotated @NullMarked should not override non-annotated parameter">param</warning>) {}
}

// Multiple inheritance: class + generic interface

class UnmarkedBase3 {
  public void a(String a) {}
}

interface UnmarkedGenericInterface<T extends Object> {
  void a(T a);
}

@NullMarked
class NullMarkedWithMultipleSupers extends UnmarkedBase3 implements UnmarkedGenericInterface<String> {
  @Override
  public void a(String <warning descr="Parameter annotated @NullMarked should not override non-annotated parameter">a</warning>) {}
}
