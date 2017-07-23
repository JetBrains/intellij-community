// "Replace method call on lambda with lambda body" "true"

import java.util.*;
import java.util.function.*;

public class Test {
    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        if(list.isEmpty())
            ((Consumer<String>) x -> {
                System.out.println(x);
                list.add(x);
            }).<caret>accept("foo");
    }
}