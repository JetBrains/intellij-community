import org.jetbrains.annotations.NotNull;

class Test1<U> {

    public static void main(String[] args) {
        Comparable<? extends Integer> c = new Comparable<Integer>() {
            @Override
            public int compareTo(@NotNull Integer o) {
                return 0;
            }
        };
    }
}