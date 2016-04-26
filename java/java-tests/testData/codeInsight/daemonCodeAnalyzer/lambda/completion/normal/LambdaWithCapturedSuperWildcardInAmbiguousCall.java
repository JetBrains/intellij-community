import java.util.stream.*;
import java.util.*;

class Foo {
  void main(Stream<? super String> stream) {
    stream.collect(Collectors.toMap(o -> o.sub<caret>))
  }
}