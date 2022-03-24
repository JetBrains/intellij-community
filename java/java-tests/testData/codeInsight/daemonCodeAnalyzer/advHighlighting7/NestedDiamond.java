import java.util.ArrayList;
import java.util.List;

class Test {

  {
    <error descr="Incompatible types. Found: 'java.util.ArrayList<java.util.List>', required: 'java.util.List<java.util.List<java.lang.String>>'">List<List<String>> l = new ArrayList<List<>>();</error>
  }
}