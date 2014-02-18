import java.util.*;

class MyClass implements Comparator<String> {

    <error descr="Static method 'compare(String, String)' in 'MyClass' cannot override instance method 'compare(T, T)' in 'java.util.Comparator'">public static int compare(String a, String b)</error> {
      return 42;
    }
} 