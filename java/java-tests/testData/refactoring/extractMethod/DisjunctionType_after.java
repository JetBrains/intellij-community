public class DisjunctionType {
    static void test() {
        try {
            foo();
        }
        catch (NoSuchFieldException | NoSuchMethodException ex) {

            newMethod(ex);

        }
    }

    private static void newMethod(ReflectiveOperationException ex) {
        System.out.println(ex.getCause());
    }
}