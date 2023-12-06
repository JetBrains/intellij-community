import org.checkerframework.checker.tainting.qual.Untainted;
import java.util.Arrays;
import java.util.List;

class ForEachLoop {

  public void lambdaIterate() {
    List<String> queries = Arrays.asList("select c from sample s","select c.sample from Sample c where c.color = 'red");

    queries.forEach(query -> sink(query));
  }
  public void lambdaIterateDirty(String untidy) {
    List<String> queries = Arrays.asList("select c from sample s","select c.sample from Sample c where c.color = 'red", untidy);

    queries.forEach(query -> sink(<warning descr="Unknown string is used as safe parameter">query</warning>));
  }

  public void sink(@Untainted String clean) {

  }
}
