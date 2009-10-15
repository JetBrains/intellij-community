import java.util.Collection;

class Foo {

  Collection<Foo> foos() {}

  {
    Foo[] f = foos().toArray(new Foo[0]);<caret>
  }

}
