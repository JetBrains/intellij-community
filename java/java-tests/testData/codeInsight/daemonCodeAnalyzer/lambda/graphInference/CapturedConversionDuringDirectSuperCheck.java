import java.util.List;
import java.util.Set;
import java.util.function.Function;

class Test {
  void f(List<? extends I<?>> list) {
    foo(list.get(0));
  }

  private <T> T foo(I<T> id) {
    return null;
  }

  interface I<Z> {
  }
}

class Test1 {

  private static void getMarketDataValues(ScenarioMarketData marketData,
                                          Set<? extends MarketDataId<?>> ids) {

    ids.add(bar(marketData::getValue));
  }

  interface ScenarioMarketData {
    <T> MarketDataBox<T> getValue(MarketDataId<T> id);
  }

  interface MarketDataBox<J> {
  }

  interface MarketDataId<M> {
  }

  static <T, V> T bar(Function<T, V> valueExtractor) {
    return null;
  }
}