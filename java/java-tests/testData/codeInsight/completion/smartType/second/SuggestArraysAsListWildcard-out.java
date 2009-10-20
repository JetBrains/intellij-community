import java.util.Arrays;
import java.util.Collection;

interface Bar {}

class Foo implements Bar {

  {
    Foo[] foos;
    Collection<? extends Bar> c = Arrays.asList(foos);<caret>
  }

}