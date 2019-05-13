// "Replace method call on lambda with lambda body" "true"

import java.util.*;
import java.util.function.*;

public class Test {
    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        if(list.isEmpty()) {
            System.out.println("foo");
            list.add("foo");
        }
    }
}