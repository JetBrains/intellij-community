import java.util.*;

public class NestedArray {
    public NestedArray() {}

    public void method( Object ... args ) {
        for (int i = 0; i < args.length; i++) {
            System.out.println("args["+i+"] = " + args[i]);
        }
    }

    public void main(String[] args) {
        String[] params = new String[]{ "0", "1" };
        method(new Object[]{params});
        method(<warning descr="Redundant array creation for calling varargs method">new Object[]</warning>{"2", params});
        method(<warning descr="Redundant array creation for calling varargs method">new Object[]</warning>{params, params});
    }

    public static Collection quickFixErrorIDEA165068() {
        return Arrays.asList(new String[][]{
          {"bla", " bla"},
          {"bla", " bla"},
          {"bla", " bla"},
          {"bla", " bla"},
        });
    }

    public static Collection quickFixError2() {
        return Arrays.asList(<warning descr="Redundant array creation for calling varargs method">new String[][]</warning>{
          new String[] {"bla", " bla"},
          new String[] {"bla", " bla"},
          new String[] {"bla", " bla"},
          new String[] {"bla", " bla"},
        });
    }
}