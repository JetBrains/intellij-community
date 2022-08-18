import java.util.function.Function;


class MyTest {
   <T> java.util.List<T> f(Function<T, String> ff) {
     return null;
   }

    {
        this.<String>f<caret>(s -> {
            switch (s) {
                case null :
                    System.out.println();
                    break;
                default: return "";
            }
        });
    }
}
