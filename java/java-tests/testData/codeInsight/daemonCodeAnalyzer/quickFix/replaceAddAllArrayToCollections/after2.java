// "Replace addAll(objs) with java.util.Collections.addAll(this, objs)" "true"
import java.util.Collections;
import java.util.List;

class A implements List {
  public static void main(String[] args){
    Object[] objs = new Object[0];
    Collections.addAll(this, objs);
  }
}