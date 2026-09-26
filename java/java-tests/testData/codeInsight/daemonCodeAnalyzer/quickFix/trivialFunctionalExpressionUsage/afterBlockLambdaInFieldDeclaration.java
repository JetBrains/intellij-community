// "Replace method call on lambda with lambda body" "true-preview"
import java.util.function.Supplier;

class Test {
  String str;

    {
        String s = "";
        str = s;
    }
}