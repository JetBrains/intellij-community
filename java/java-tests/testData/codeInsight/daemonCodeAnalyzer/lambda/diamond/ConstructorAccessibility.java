interface Either {
    public static final class Left<L>  {
        private final L value;

        private Left(L value) {
            this.value = value;
        }
    }
}

class Main {
    {
        new <error descr="'Left(L)' has private access in 'Either.Left'">Either.Left<></error>("");
    }
}