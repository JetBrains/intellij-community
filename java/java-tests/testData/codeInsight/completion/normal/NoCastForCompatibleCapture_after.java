import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

class Foo {
  {
    List<? extends List<? extends Future<?>>> list = new ArrayList<>();
    list.stream().map(l -> l.stream().map(f -> f.isDone()<caret>
  }
}