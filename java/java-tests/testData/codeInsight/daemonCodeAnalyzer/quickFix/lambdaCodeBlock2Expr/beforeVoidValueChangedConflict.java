// "Replace with expression lambda" "false"
import java.util.HashSet;
import java.util.Set;

class Test {
  public static void main(String[] args) {
    Set<String> strings = new HashSet<>();
    new Test().query(pResultSet -> <caret>{
      strings.add("Col1");
    });
  }

  public void query(RowCallbackHandler rch){
    System.out.println();
  }

  public Object query( final ResultSetExtractor rse) {
    return null;
  }
}

interface RowCallbackHandler {
  void processRow(ResultSet var1);
}

interface ResultSetExtractor {
  Object extractData(ResultSet var1);
}
class ResultSet {}