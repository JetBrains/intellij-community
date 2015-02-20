class Test {
  public interface A<E extends Throwable> {
    Object call() throws E;
  }

  public interface B<E extends Throwable> {
    void call() throws E;
  }

  static Object method(A lambda) {
    System.out.println("A::");
    try {
      lambda.call();
    } catch (Throwable throwable) {
      throwable.printStackTrace();
    }
    return null;
  }

  static void method(B lambda) {
    System.out.println("B::");
    try {
      lambda.call();
    } catch (Throwable throwable) {
      throwable.printStackTrace();
    }
  }

  static Object returns(String s) throws Exception {
    System.out.println(s); return null;
  }

  static void voids(String s) throws Exception {
    System.out.println(s);
  }

  public static void main(String[] args) {

    method(() -> {
      voids("-> B");
    });


    method(() -> voids("-> B"));

    method(() -> {
      return returns("-> A");
    });

    method(() ->  returns("-> A") );

    method(() -> {
      returns("-> B");
    });
  }


}
