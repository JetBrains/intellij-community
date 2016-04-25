import java.util.ArrayList;
import java.util.List;

class Test {

  {
    List<List<String>> l = new ArrayList<List<<error descr="Cannot infer arguments"></error>>>();
  }
}