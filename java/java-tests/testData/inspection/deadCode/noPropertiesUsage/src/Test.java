import java.util.Collections;

class Test {
  public static void main(String[] args) {
    var parentList = Collections.singletonList(new ChildJava());
    for (var parent : parentList) {
    }
  }
}

interface ParentJava {
  String getSomeString();

  void setSomeString(String str);
}

class ChildJava implements ParentJava {
  private String someString;

  @Override
  public String getSomeString() {
    return someString;
  }

  @Override
  public void setSomeString(String str) {
    this.someString = str;
  }
}
