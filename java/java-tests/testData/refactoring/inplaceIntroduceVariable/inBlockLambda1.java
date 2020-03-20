import java.util.function.Supplier;

class C {
    Supplier<Supplier<String>> test(String s) {
        System.out.println(s.trim()+s.trim());
        return () -> {
            System.out.println(s.trim()+s.trim());
            return () -> {
                return s.t<caret>rim() + s.trim();
            };
        };
    }
}