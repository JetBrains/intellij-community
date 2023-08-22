// "Assert 'list != null'" "true-preview"
import java.util.List;

class A{
  void test(List<String> list){
    if(list == null) {
      System.out.println("oops");
    }
    for(String s : li<caret>st) {
      
    }
  }
}