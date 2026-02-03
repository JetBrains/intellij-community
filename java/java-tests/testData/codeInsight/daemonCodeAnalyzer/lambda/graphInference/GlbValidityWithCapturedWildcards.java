
interface Condition<T> {
  boolean value(T t);
}
abstract class Test {

  protected static <Impl extends AbstractCache, T extends OCSymbol> T findNearestTopLevelSymbol(Class<Impl> clazz,
                                                                                                Condition<? super T> condition) {

    return null;
  }

  interface OCSymbol {}
  interface SwiftSymbol extends OCSymbol {}


  class AbstractCache<T extends OCSymbol> {}
  class SwiftCache extends AbstractCache<SwiftSymbol> {}

  private static void foo(final Condition<? super SwiftSymbol> condition) {
    SwiftSymbol s = findNearestTopLevelSymbol(SwiftCache.class, condition);
  }
  private static void foo1(final Condition<? extends SwiftSymbol> condition) {
    SwiftSymbol s = findNearestTopLevelSymbol(SwiftCache.class, condition);
  }
}
