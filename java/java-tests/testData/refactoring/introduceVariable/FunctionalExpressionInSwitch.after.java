import java.util.function.Predicate;
class Foo {
    Predicate<String> test(int i) {
       return switch (i) {
           default -> {
               final Predicate<String> p = String::isEmpty;
               break p;
           }
       };
    }
}