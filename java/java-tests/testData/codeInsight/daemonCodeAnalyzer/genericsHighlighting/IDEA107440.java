class GoodCodeRed {
    static class ClassA {
    }

    static class Pair<T, N> {
    }

    <T extends ClassA, M extends T, N extends Number> void max(final M object, Pair<T, N> attribute, final N cnt) {
    }

    <T extends ClassA, M extends T, N extends String> void max(final M object, Pair<T, N> attribute, final N str) {
    }


    {
        max(new ClassA(), new Pair<ClassA, Integer>(), 1);
    }
}