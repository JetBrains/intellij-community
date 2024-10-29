class Main {
  public static void main(String[] args) {

    interface Add {
      static OptionalLong set(long a) {
        return null;
      }

      interface Async {
        /**
         * @see Add#set(long)
         */
        CompletableFuture<OptionalLong> set(long a);

        static void x() {
          Add.set(1);
        }
      }
    }
  }
}