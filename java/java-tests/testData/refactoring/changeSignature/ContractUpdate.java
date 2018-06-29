import org.jetbrains.annotations.Contract;

public class C {
    @Contract(value = "null, _, _ -> param3; !null, _, _ -> param1", pure = true)
    public static String te<caret>st(String o1, Object obj, String o2) {
        return o1 == null ? o2 : o1;
    }
}
