class ClassA {
  public abstract class InnerAbstractA {
  }
}

class ClassC {
  static ClassA classA = new ClassA();

  public static ClassA getClassA() {
    return classA;
  }
}

class ClassB {
  public static class InnerClassA extends ClassA.InnerAbstractA {
    public InnerClassA() {
      ClassC.getClassA().super();
    }
  }
}