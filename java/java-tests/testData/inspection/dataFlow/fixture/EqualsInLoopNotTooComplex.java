import java.util.List;

class Test {
  enum A {X, Y, Z};
  List<String> list1, list2;
  
  void test(List<String> tokens) {
    String t = "int";
    A s = A.X;
    A l = A.X;
    for (String token : tokens) {
      if ("unsigned".equals(token)) {
        s = A.Z;
      }
      else if ("signed".equals(token)) {
        s = A.Y;
      }
      else if ("short".equals(token)) {
        l = A.Y;
      }
      else if ("long".equals(token)) {
        l = A.Z;
      }
      else if (list1.contains(token) || list2.contains(token)) {
        t = token;
      }
    }
  }
}