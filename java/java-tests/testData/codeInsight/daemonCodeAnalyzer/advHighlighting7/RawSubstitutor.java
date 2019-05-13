import java.util.*;

class Sample {
  public static List getList() {return null;}
  List<Integer> list = new ArrayList<>(getList());

  void foo() {
    barz<error descr="'barz(java.util.Map<java.lang.String,java.lang.String[]>)' in 'Sample' cannot be applied to '(java.util.HashMap<java.lang.Object,java.lang.Object>)'">(new HashMap<>(create()))</error>;
  }

  Map create() {
    return null;
  }

  void barz(Map<String, String[]> map) {}
}
