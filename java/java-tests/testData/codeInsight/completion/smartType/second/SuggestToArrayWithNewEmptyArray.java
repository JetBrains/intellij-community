import java.util.Collection;

class Foo {

  Collection<Foo> foos() {}

  {
    Foo[] f = <caret>
  }

}
