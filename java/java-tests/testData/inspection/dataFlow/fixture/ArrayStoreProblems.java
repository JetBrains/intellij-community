import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

class ArrayStoreProblems {
  void testLocalClass() {
    abstract class Foo {}
    Foo[] data = new Foo[1];
    data[0] = new Foo() {};
  }
  
  void test(String[] args, Integer[] args2) {
    Object[] arr = args;
    arr[0] <warning descr="Storing element of type 'java.lang.Integer' to array of 'java.lang.String' elements will produce 'ArrayStoreException'">=</warning> 123;
    arr = args2;
    arr[1] = 124;
    arr[2] <warning descr="Storing element of type 'java.lang.String' to array of 'java.lang.Integer' elements will produce 'ArrayStoreException'">=</warning> "foo";
    arr = args;
    arr[3] = "bar";
    arr[4] <warning descr="Storing element of type 'java.lang.Integer' to array of 'java.lang.String' elements will produce 'ArrayStoreException'">=</warning> 125;
  }
  
  void test2(Object obj) {
    Object[] arr = new String[10];
    arr[0] = obj;
    arr[1] <warning descr="Storing element of type 'java.lang.Object' to array of 'java.lang.String' elements will produce 'ArrayStoreException'">=</warning> new Object();
  }
  
  void test3(Number n) {
    Object[] arr = new CharSequence[10];
    arr[0] = n; // could implement CharSequence
    arr[1] = "foo";
    arr[2] <warning descr="Storing element of type 'java.lang.Integer' to array of 'java.lang.CharSequence' elements will produce 'ArrayStoreException'">=</warning> 123;
    arr[3] <warning descr="Storing element of type 'java.lang.Object' to array of 'java.lang.CharSequence' elements will produce 'ArrayStoreException'">=</warning> new Object();
  }
}