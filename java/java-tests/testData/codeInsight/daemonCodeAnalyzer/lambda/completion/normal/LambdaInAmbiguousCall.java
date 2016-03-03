import java.util.stream.*;

class Foo {
  {
    Collectors.toMap(l -> l.t<caret>)
  }
}