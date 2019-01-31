package bytecodeAnalysis.data;

import bytecodeAnalysis.ExpectNoPsiKey;

/**
 * @author lambdamix
 */
public class TestConverterData {
  public static class StaticNestedClass {
    public StaticNestedClass(Object o) { }

    public StaticNestedClass[] test01(StaticNestedClass[] ns, StaticNestedClass... ellipsis) {
      return ns;
    }
  }

  public class InnerClass {
    // a reference to outer class should be inserted when translating PSI -> ASM
    public InnerClass(Object o) { }

    public InnerClass[] Inner2test01(InnerClass[] tests, InnerClass... ellipsis) {
      return tests;
    }
  }

  public static class GenericStaticNestedClass<A> {
    public GenericStaticNestedClass(A a) { }

    public GenericStaticNestedClass[] test01(GenericStaticNestedClass[] ns, GenericStaticNestedClass... ellipsis) {
      return ns;
    }

    public GenericStaticNestedClass<A>[] test02(GenericStaticNestedClass<A>[] ns, GenericStaticNestedClass<A>... ellipsis) {
      return ns;
    }

    public class GenericInnerClass<B> {
      public GenericInnerClass(B b) { }

      public <C> GenericStaticNestedClass<A> test01(GenericInnerClass<C> c) {
        return GenericStaticNestedClass.this;
      }
    }
  }

  public TestConverterData(int x) { }

  // ExtConverterClass class is not in the class roots, so translation from PSI is impossible
  @ExpectNoPsiKey
  public ExtTestConverterData test01(ExtTestConverterData converter) {
    return converter;
  }

  @TestAnnotation
  public TestConverterData[] test02(@TestAnnotation TestConverterData[] tests) {
    return tests;
  }

  public boolean[] test03(boolean[] b) {
    return b;
  }
}

class ExtTestConverterData { }