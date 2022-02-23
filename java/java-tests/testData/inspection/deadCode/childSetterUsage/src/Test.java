class Test {
  public static void main(String[] args) {
    var child = new ChildJava();
    child.setSomeString("World");
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

