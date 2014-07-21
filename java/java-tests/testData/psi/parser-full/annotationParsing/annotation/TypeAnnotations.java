import java.util.@NotAllowed Date;      // illegal
import @IllegalSyntax java.util.Date;   // illegal

class SpecSamples {

  //
  // 1. A type annotation appears before the type’s simple name ...
  //

  Map<@NonNull String, @NonEmpty List<@Readonly Document>> files;

  { o.<@NonNull String>m("..."); }

  class Folder<F extends @Existing File> { }

  Collection<? super @Existing File> files;

  class UnmodifiableList<T> implements @Readonly List<@Readonly T> { }

  void monitorTemperature() throws @Critical TemperatureException { }

  {
    new @Interned MyObject();
    new @NonEmpty @Readonly List<String>(myNonEmptyStringSet);
    myVar.new @Tainted NestedClass();

    new <String> @Interned MyObject();

    Map.@NonNull Entry ee;

    myString = (@NonNull String) myObject;
    myString = (@NonNull) myObject; // error: type omitted

    isNonNull = myString instanceof @NonNull String;
    isNonNull = myString instanceof @NonNull; // error: type omitted

    m(@Vernal Date::getDay);
    m(@IllegalAnnotation java.util.@Vernal Date::getDay);
    m(List<@English String>::size);
    m(Arrays::<@NonNegative Integer>sort);

    m((@TA Object x, @TB Object y) -> { System.out.println("x=" + x + " y=" + y); });

    try { m(); }
    catch (@A1 NullPointerException | @A2 IllegalArgumentException e) { }

    try (@A Reader r = new @B FileReader("/dev/zero"); @A Writer w = new @B FileWriter("/dev/null")) { }
  }

  interface TestClass {
    <T> @Nullable List<T> test();
    <T> @Positive int test(T t);
  }

  //
  // 2. An annotation on a wildcard type argument appears before the wildcard ...
  //

  void m(MyClass<@Immutable ? extends Comparable<MyClass>> p) { }

  //
  // 3. The annotation on a given array level prefixes the brackets that introduce that level ...
  //

  void m(Document @Readonly ... docs) {
    @Readonly Document [][] docs1 = new @Readonly Document [2][12]; // array of arrays of read-only documents
    Document @Readonly [][] docs2 = new Document @Readonly [2][12]; // read-only array of arrays of documents
    Document[] @Readonly [] docs3 = new Document[2] @Readonly [12]; // array of read-only arrays of documents

    @NonNegative int @NonEmpty [] ints = new @NonNegative int @MinSize(2) [2];
  }

  int m() @Slowpoke [] @Slowbro [] { return null; }

  //
  // 4. A type annotation is permitted in front of a constructor declaration ...
  //

  @Immutable SpecSamples() { }
  <T> @Immutable SpecSamples(T t) { }

  //
  // todo [r.sh] 5. It is permitted to explicitly declare the method receiver as the first formal parameter ...
  //

  /*public String toString(@Readonly MyClass this) {  }

  public boolean equals(@Readonly MyClass this, @Readonly Object other) {  }

  SpecSamples(@Receiver SpecSamples this, boolean b) { }

  class Outer {
    class Middle {
      class Inner {
        void innerMethod(@A Outer. @B Middle. @C Inner this) { }
      }
    }
  }

  void replace(@Readonly Object other, @Mutable MyClass this) {  } // illegal */

  //
  // 6. It is permitted to write an annotation on a type parameter declaration ...
  //

  class MyClass<@Immutable T> { }

  interface WonderfulList<@Reified E> { }

}
