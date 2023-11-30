class SimpleClass {

  public SimpleClass() {
  }

    public static SimpleInnerClass createSimpleInnerClass() {
        return new SimpleInnerClass();
    }

    public static class SimpleInnerClass {
    private SimpleInnerClass() {
    }
  }
}

class Main {

  public static void main(String[] args) {

    SimpleClass.SimpleInnerClass s2 = SimpleClass.createSimpleInnerClass();

  }
}