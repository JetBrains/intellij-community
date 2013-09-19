import java.util.*;
class A {
    public static void main(String[] args) {
        List<String> strings = new ArrayList<>();

        List<? super Integer> list = new ArrayList<>();
        list.addAll<error descr="'addAll(java.util.Collection<? extends capture<? super java.lang.Integer>>)' in 'java.util.List' cannot be applied to '(java.util.List<java.lang.String>)'">(strings)</error>;

        System.out.println(list.toString());
    }
}
