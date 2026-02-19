class SimpleClass {

  public SimpleClass() {
  }

  public static class SimpleInnerClass {
    public <caret>SimpleInnerClass() {
    }
  }
}

class Main {

  public static void main(String[] args) {

    SimpleClass.SimpleInnerClass s2 = new SimpleClass.SimpleInnerClass();

  }
}