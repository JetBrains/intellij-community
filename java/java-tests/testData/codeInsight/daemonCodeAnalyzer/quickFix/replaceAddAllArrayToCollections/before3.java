// "Replace l.removeAll(objs) with java.util.Collections.addAll(l, objs)" "false"
class T {
  public static void main(String[] args){
    List l = new List();
    Object[] objs = new Object[0];
    l.removeAll(<caret>objs);
  }
}