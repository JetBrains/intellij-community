import java.util.function.IntFunction;

class Test {
  {
    IntFunction<Class<? extends String>[]> var1 = value -> {
      return <warning descr="Unchecked assignment: 'java.lang.Class[]' to 'java.lang.Class<? extends java.lang.String>[]'">new Class[value]</warning>;
    };
    System.out.println(var1);

    IntFunction<Class<? extends String>[]> var2 = value -> <warning descr="Unchecked assignment: 'java.lang.Class[]' to 'java.lang.Class<? extends java.lang.String>[]'">new Class[value]</warning>;
    System.out.println(var2);
  }
}