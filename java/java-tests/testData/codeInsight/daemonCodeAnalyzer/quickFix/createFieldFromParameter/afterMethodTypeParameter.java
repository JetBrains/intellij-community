// "Create field for parameter 'array'" "true-preview"

class X {
    private static Object[] array;

    private static <T> void x(T[] array){
        X.array = array;
    }
}