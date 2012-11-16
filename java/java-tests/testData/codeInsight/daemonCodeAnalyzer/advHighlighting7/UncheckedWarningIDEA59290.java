import java.util.*;
class Test {
  { 
    List<? super CharSequence> list = new ArrayList<CharSequence>();
    List<? super String> foo = list;
    System.out.println(foo);

    List<? extends String> list1 = new ArrayList<String>();
    List<? extends CharSequence> foo1 = list1;
    System.out.println(foo1);
  }
}