import org.jetbrains.annotations.Contract;

class C {
    @Contract("null, _ -> false")
    public static boolean checkSomething(Object o1, Object <caret>o2) {
        if(!(o1 instanceof String)) return false;
        return Math.random() > 0.5;
    }
}