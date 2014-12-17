// "Replace with expression lambda" "true"
import java.util.HashSet;
import java.util.Set;

class Test {
  public static void main(String[] args) {
    Set<String> strings = new HashSet<>();
    new Test().query("", pResultSet -> <caret>{
      strings.add("Col1");
    });
  }

  public Object query(String s, final ResultSetExtractor rse) {
    return null;
  }

  public Object query(String s, final ResultSetExtractor rse, Object.. args) {
    return null;
  }
}

interface ResultSetExtractor {
  Object extractData(ResultSet var1);
}
class ResultSet {}