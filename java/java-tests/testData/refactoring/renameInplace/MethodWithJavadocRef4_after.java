class Main {
  public static void main(String[] args) {

    new Object() {
      static OptionalLong set(long a) {
        return null;
      }

      interface Async {
        /**
         * @see #set(long)
         * incorrect but can't be fixed
         */
        CompletableFuture<OptionalLong> set(long a);

        static void x() {
          set(1); // incorrect but can't be fixed
        }
      }
    };
  }
}