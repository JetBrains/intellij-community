// "Replace l.addAll(objs) with java.util.Collections.addAll(l, objs)" "true"
import java.util.Collections;
import java.util.List;

class A {
  public static void main(String[] args){
    List l = new List();
    Object [] objs = new Object[0];
    Collections.addAll(l, objs);
  }
}