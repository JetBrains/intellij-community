public class IfOnClass {
  public static void main(String[] args) {
  }

  public static void test(BaseClass<?> baseClass) {
    Class<?> clazz = baseClass.getClass().getSuperclass();

    <caret>if (Class1.class.equals(clazz)) {
      System.out.println("1");
    } else if (Class2.class.equals(clazz)) {
      System.out.println("2");
    } else if (Class3.class.equals(clazz)) {
      System.out.println("3");
    } else {
      throw new IllegalArgumentException();
    }
  }
}

class SomeClass {
}

class Class1 extends SomeClass {
}

class Class2 extends SomeClass {
}

class Class3 extends SomeClass {
}

class BaseClass<T extends SomeClass> {
}