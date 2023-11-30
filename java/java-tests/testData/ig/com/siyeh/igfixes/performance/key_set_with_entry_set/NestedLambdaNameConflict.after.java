import java.util.*;

public class NestedLambdaNameConflict {
  public void test(Map<String, String> m){
    for(Map.Entry<String, String> e : m.entrySet()) {
      System.out.println(e.getKey());
      Foo foo = entry -> entry.getKey().equals(e.getValue());
    }
  }
  
  interface Foo {
    boolean doSmth(Map.Entry<String, String> e);
  }
}