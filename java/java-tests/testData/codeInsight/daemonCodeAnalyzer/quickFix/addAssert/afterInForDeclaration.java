// "Assert 'list != null'" "true-preview"
import java.util.List;

class A{
  void test(List<String> list){
    if(list == null) {
      System.out.println("oops");
    }
      assert list != null;
      for(String s : list) {
      
    }
  }
}