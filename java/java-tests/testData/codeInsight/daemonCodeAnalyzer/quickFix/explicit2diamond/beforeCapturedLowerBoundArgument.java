// "Replace with <>" "false"

import java.util.*;
import java.util.function.Predicate;

class Test {
    @SafeVarargs
    public static <T> Predicate<T> all(Predicate<? super T>... predicates) {
        return new AllPredicate<<caret>T>(Arrays.asList(predicates));
    }

    private static class AllPredicate<T> implements Predicate<T> {
        private final List<Predicate<? super T>> predicates;

        AllPredicate(List<Predicate<? super T>> predicates) {
            this.predicates = predicates;
        }

        @Override
        public boolean test(T t) {
            for (Predicate<? super T> predicate : predicates) {
                if (!predicate.test(t)) return false;
            }
            return true;
        }
    }
}