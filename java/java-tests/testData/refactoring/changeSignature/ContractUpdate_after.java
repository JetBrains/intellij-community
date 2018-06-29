import org.jetbrains.annotations.Contract;

public class C {
    @Contract(value = "_, null, _ -> param1; _, !null, _ -> param2", pure = true)
    public static String test(String a, String b, String c) {
        return b == null ? a : b;
    }
}
