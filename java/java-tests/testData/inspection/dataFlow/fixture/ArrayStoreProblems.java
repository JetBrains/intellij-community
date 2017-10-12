import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

class ArrayStoreProblems {
  void test(String[] args, Integer[] args2) {
    Object[] arr = args;
    arr[0] <warning descr="Storing element of type 'int' to array of 'java.lang.String' elements may produce 'java.lang.ArrayStoreException'">=</warning> 123;
    arr = args2;
    arr[1] = 124;
    arr[2] <warning descr="Storing element of type 'java.lang.String' to array of 'java.lang.Integer' elements may produce 'java.lang.ArrayStoreException'">=</warning> "foo";
    arr = args;
    arr[3] = "bar";
    arr[4] <warning descr="Storing element of type 'int' to array of 'java.lang.String' elements may produce 'java.lang.ArrayStoreException'">=</warning> 125;
  }
}