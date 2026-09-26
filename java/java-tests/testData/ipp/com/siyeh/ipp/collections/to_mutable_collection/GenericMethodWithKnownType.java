import java.util.*;

class Test {

  <T> void test(T obj) {
    System.out.println(Collections.singleton<caret>(obj));
  }

}