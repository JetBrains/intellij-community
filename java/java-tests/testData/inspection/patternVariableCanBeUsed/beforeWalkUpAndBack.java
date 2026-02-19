// "Replace cast expressions with pattern variable" "true"
public final class A {
  public void test2(Object o) {
    if(!(o instanceof String)){
      return;
    }
    if(o instanceof List){
      return;
    }
    System.out.println("1");

    if (o instanceof Object) {
      System.out.println(((String) o).isEmpty());
      for (int i = 0; i < ((String) o).length(); i++) {
        System.out.println(((S<caret>tring) o).charAt(i));
      }
    }
  }
}