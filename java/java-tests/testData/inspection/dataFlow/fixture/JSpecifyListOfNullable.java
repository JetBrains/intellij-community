import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NullMarked
class Test2 {

  List<? extends Bar> call(SomethingNullable<?> somethingNullable) {
    Bar o = somethingNullable.get().get(0);
    System.out.println(o.<warning descr="Method invocation 'toString' may produce 'NullPointerException'">toString</warning>());
    return somethingNullable.get();
  }
}

class Bar {
}

interface SomethingNullable<T extends Bar> {
  List<@Nullable T> get();
}