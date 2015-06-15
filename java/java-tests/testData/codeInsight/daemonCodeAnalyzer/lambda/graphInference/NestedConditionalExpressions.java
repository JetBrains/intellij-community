import java.util.ArrayList;
import java.util.List;

class C {
  public static void main(Object o) {
    List l = (List)(o instanceof ArrayList ? (ArrayList)o : o instanceof List ? (List)o : o);
  }
}