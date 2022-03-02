import java.util.Collections;
import java.util.List;

class Test {
  public static void main(String[] args) {
    for (var parent : getList()) {
      System.out.println(parent.getSomeString());
    }
  }

  static List<ParentJava> getList() {
    return Collections.singletonList(new ChildJava());
  }
}


interface ParentJava {
  String getSomeString();
  void setSomeString(String str);
}

class ChildJava implements ParentJava {
  private String someString = "Hello";

  @Override
  public String getSomeString() {
    return someString;
  }

  @Override
  public void setSomeString(String str) {
    this.someString = str;
  }
}
