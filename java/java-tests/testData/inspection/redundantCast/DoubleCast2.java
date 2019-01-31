import java.util.List;
import java.util.ArrayList;

class Test{
  static void f(){
    Object o = null;
    ArrayList list = (ArrayList)((<warning descr="Casting 'o' to 'List' is redundant">List</warning>)o);
  }
}
