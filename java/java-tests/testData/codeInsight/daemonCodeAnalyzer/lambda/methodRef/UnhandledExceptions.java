public class ExTest {
    public static void maybeThrow(String data) throws Ex {
        throw new Ex(data);
    }

    {
      Block<String> b = <error descr="Unhandled exception: ExTest.Ex">ExTest::maybeThrow</error>;
    }


    private static class Ex extends Throwable {
        public Ex(String s) {
        }
    }
}

interface Block<T> {
    public void accept(T t);
}