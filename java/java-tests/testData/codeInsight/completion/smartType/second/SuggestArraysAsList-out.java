import java.util.Arrays;
import java.util.Collection;

class Foo {

  {
    Foo[] foos;
    Collection<Foo> c = Arrays.asList(foos);<caret>
  }

}