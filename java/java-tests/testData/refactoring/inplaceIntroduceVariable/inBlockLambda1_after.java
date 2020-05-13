import java.util.function.Supplier;

class C {
    Supplier<Supplier<String>> test(String s) {
        System.out.println(s.trim()+s.trim());
        return () -> {
            System.out.println(s.trim()+s.trim());
            return () -> {
                String trim = s.trim();
                return trim + trim;
            };
        };
    }
}