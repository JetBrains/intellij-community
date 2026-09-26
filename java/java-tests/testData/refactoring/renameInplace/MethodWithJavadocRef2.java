class Main {
  public static void main(String[] args) {

  }

  interface Add {
    static OptionalLong set(long a) {
      return null;
    }

    interface Async {
      /**
       * @see #set(long)
       */
      CompletableFuture<OptionalLong> add<caret>(long a);

      static void x() {
        set(1);
      }
    }
  }
}