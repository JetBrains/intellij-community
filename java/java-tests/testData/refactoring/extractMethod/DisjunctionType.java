public class DisjunctionType {
    static void test() {
        try {
            foo();
        }
        catch (NoSuchFieldException | NoSuchMethodException ex) {
        <selection>
         System.out.println(ex.getCause());
        </selection>
        }
    }
}