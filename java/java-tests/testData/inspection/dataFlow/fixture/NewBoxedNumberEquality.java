import java.util.*;

class Testcase {

  private static final Long TEST = new Long(1234);

  public static void main(String[] args) {
    final Map<String, Long> hashMap = new HashMap<>();
    hashMap.put("Hello", TEST);

    Long l = hashMap.get("Hello");
    if(l != null) {
      if(l == TEST) {
        System.out.println("Out");
      }
    }
  }

}