import java.util.ArrayList;
import java.util.List;

class Foo<T> {

  public void test(Foo parent) {
    List<Foo> elements = getElements(parent);


    /*for (Foo foo : getElements(parent)) {
      System.out.println(foo);
    }*/
    
    for (Foo foo : getElementsArray(parent)) {
      System.out.println(foo);
    }
  }

  public static <E extends Foo<E>> List<E> getElements(E parent) {
    return new ArrayList<>();
  }
  
  public static <E extends Foo<E>> E[] getElementsArray(E parent) {
    return null;
  }
}
