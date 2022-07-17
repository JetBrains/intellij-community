// "Surround with try/catch" "true"
public class ExTest {
    public static void maybeThrow(String data) throws Ex {
        throw new Ex(data);
    }

    {
        Block<String> b = (t) -> {
            try {
                return ExTest.maybeThrow(t);
            } catch (Ex e) {
                <selection>throw new RuntimeException(e);</selection>
            }
        };
    }


    private static class Ex extends Throwable {
        public Ex(String s) {
        }
    }
}

interface Block<T> {
    public void accept(T t);
}