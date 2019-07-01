import java.util.function.Predicate;
class Foo {
    Predicate<String> test(int i) {
       return switch (i) {
           default -> <selection>String::isEmpty</selection>;
       };
    }
}