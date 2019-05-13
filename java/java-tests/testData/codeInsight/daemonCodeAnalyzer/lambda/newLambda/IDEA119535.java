import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;

import static java.util.stream.Collectors.*;

class Stuff {
  public enum Type { A }
  private final int value;
  private final Type type;
  public Stuff(int value, Type type) {
    this.value = value;
    this.type = type;
  }
  public int getValue() {
    return value;
  }
  public Type getType() {
    return type;
  }
}

class FakeErrors {
  {

    Collector<Stuff, ?, Map<Stuff.Type, Optional<Stuff>>> collector =
      groupingBy(Stuff::getType,
                 reducing((d1, d2) -> {
                   boolean b = d1.getValue() > d2.getValue();
                   return d1;
                 }));
  }

  public static <T> Collector<T, ?, Optional<Stuff>> reducing(BinaryOperator<T> op) {
    return null;
  }

  public static <T, K, A, D>
  Collector<T, ?, Map<K, D>> groupingBy(Function<? super T, ? extends K> classifier,
                                        Collector<? super T, A, D> downstream) {
    return null;
  }
}

class FakeErrorsComplete {
  public static List<Stuff> elems(){
    return Arrays.asList(
      new Stuff(800, Stuff.Type.A));
  }

  public static void main(String ... args){

    Map<Stuff.Type, Optional<Stuff>> bar =
      elems()
        .stream()
        .collect(groupingBy(Stuff::getType,
                            reducing((d1, d2) -> d1.getValue() > d2.getValue() ? d1 : d2)));

    System.out.println(bar);

    Map<Stuff.Type, Stuff> baz =
      elems()
        .stream()
        .collect(groupingBy(Stuff::getType,
                            collectingAndThen(reducing((Stuff d1, Stuff d2) -> d1.getValue() > d2.getValue() ? d1 : d2),
                                              Optional::get)));

    System.out.println(baz);
  }
}