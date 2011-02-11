import java.util.List;

class A {
    static List<String> listOf(String... elements) {
        System.out.println("in varargs");
        return null;
    }

    static List<String> listOf(Object elements) {
        System.out.println("in nonvarargs");
        return null;
    }

    public static void main(String[] args) {
        //resolves to nonvarargs method
        List<String> uhoh =   <ref>listOf("");
    }
}