// "Remove the 'contains' check" "true"
import java.util.List;

class Test {
  void test(List<String> list, String key) {
    if(key !=/*nullcheck*/ null && !key./*check*/isEmpty()  /*and*/ /*key*/ //line comment
    ) {
      list.remove(key);
    }
  }
}