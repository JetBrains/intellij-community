import java.util.ArrayList;
import java.util.List;

class Test {

  {
    List<List<String>> l = new <error descr="Incompatible types. Found: 'java.util.ArrayList<java.util.List>', required: 'java.util.List<java.util.List<java.lang.String>>'">ArrayList<List<>></error>();
  }
}