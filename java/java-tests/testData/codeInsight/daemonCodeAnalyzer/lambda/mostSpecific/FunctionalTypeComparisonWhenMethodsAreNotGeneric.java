class ATest {

  private void test(Function<ATest, String> nameF) {
    Function<ATest, String> aTestStringFunction  = nameF.andThen(ATest::withUnderscore);
    Function<ATest, String> aTestStringFunction1 = nameF.andThen((s) -> {return ATest.withUnderscore(s);});
    Function<ATest, String> aTestStringFunction2 = nameF.andThen((String s) -> ATest.withUnderscore(s));

    System.out.println(aTestStringFunction);
    System.out.println(aTestStringFunction1);
    System.out.println(aTestStringFunction2);
  }

  static String withUnderscore(String s) {
    return "";
  }
}

@FunctionalInterface
interface Function<A, R> {

  R apply(A a);

  default <C> Function<A, C> andThen(Function<R, ? extends C> g) { return null;}
  default Function1V<A> andThen(Function1V<R> g) {return null;}
}

@FunctionalInterface
interface Function1V<A> {
  void apply(A a);
}