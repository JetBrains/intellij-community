// "Replace cast expressions with pattern variable" "true"
public final class A {
  public void test2(Object o) {
    if(!(o instanceof String string)){
      return;
    }
    if(o instanceof List){
      return;
    }
    System.out.println("1");

    if (o instanceof Object) {
      System.out.println(string.isEmpty());
      for (int i = 0; i < string.length(); i++) {
        System.out.println(string.charAt(i));
      }
    }
  }
}