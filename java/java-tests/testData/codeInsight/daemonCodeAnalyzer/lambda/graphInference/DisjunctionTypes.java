class Test {
  public static void main(String[] args) throws Exception {
    try {
    }
    catch (Exception | Error e) {
      <error descr="Unhandled exception: java.lang.Throwable">throw identity(identity(e));</error>
    }

    try {
    }
    catch (Exception | Error e) {
      <error descr="Unhandled exception: java.lang.Throwable">throw identity(e);</error>
    }

    try {
    }
    catch (Exception | Error e) {
      throw e;
    }
  }

  public static <T> T identity(T throwable) {
    return throwable;
  }
}