
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

class Test  {
  <TValue> void iterateColumn (CollectionSerializer<TValue> valueSerializer, Consumer<TValue> consumer) {}
  private void put(Collection<String> tags){}

  void f() {
    iterateColumn(new CollectionSerializer<>(ArrayList::new), this::put);
//    iterateColumn(CollectionSerializer.create(ArrayList::new), this::put);
  }
}

class CollectionSerializer<TCollection>{
  public CollectionSerializer(final Function<Integer, TCollection> factory) {}
  static <K> CollectionSerializer<K> create(Function<Integer, K> f) { return new CollectionSerializer<>(f);}
}

