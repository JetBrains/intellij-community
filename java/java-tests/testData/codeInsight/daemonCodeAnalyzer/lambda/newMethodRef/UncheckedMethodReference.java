import java.util.*;

class Test {

  interface I<T> {
    void m(List<T> l, T el);
  }

  {
    I<String> i1 =  List::add;
    System.out.println(i1);
    I i2 = List::<warning descr="Unchecked call to 'add(E)' as a member of raw type 'java.util.List'">add</warning>;
    System.out.println(i2);
  }
}
