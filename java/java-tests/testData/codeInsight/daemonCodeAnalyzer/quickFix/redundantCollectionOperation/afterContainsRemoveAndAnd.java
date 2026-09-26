// "Remove 'contains()' check" "true-preview"
import java.util.List;

class Test {
  void test(List<String> list, String key) {
    if(key !=/*nullcheck*/ null && !key./*check*/isEmpty()  /*and*/ /*key*/ //line comment
    ) {
      list.remove(key);
    }
  }
}