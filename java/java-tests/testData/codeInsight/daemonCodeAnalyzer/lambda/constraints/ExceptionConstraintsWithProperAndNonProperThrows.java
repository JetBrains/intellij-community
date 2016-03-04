import java.io.IOException;

class GenericException {
  {
    applyFunc(t -> throwsException());
  }

  private static <T, E extends Exception> void applyFunc(CheckedFunction<T, E> function) {}


  private static void throwsException() throws Exception {}

  interface CheckedFunction<T, E extends Exception> {
    void apply(T t) throws E, IOException;
  }

}