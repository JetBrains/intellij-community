import java.util.List;

class A {
    static <T> List<T> listOf(T... elements) {
        System.out.println("in varargs");
        return null;
    }

    static <T> List<T> listOf(T elements) {
        System.out.println("in nonvarargs");
        return null;
    }

    public static void main(String[] args) {
        String[] array = {"foo", "bar"};
        //resolves to varargs method
        List<String> uhoh =   <ref>listOf(array);
    }
}