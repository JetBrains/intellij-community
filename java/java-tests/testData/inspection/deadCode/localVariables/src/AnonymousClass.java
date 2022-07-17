interface Unused {
  boolean test();
}

class Test {
  Unused lambda1 = new Unused() {

    @java.lang.Override
    public boolean test() {
      int i1 = 1;
      Unused lambda2 = new Unused() {

        @java.lang.Override
        public boolean test() {
          int i2 = 1;
          Unused lambda3 = new Unused() {

            @java.lang.Override
            public boolean test() {
              System.out.println(i2);
              int i3 = 1;
              return true;
            }
          }
          System.out.println(lambda3);
          return false;
        }
      };
      System.out.println(lambda2);
      return false;
    }
  };

  public static void main(String[] args) {
    bar(new Unused() {

      @java.lang.Override
      public boolean test() {
        int i1 = 1;
        Unused lambda2 = new Unused() {

          @java.lang.Override
          public boolean test() {
            int i2 = 1;
            Unused lambda3 = new Unused() {

              @java.lang.Override
              public boolean test() {
                System.out.println(i2);
                int i3 = 1;
                return true;
              }
            }
            System.out.println(lambda3);
            return false;
          }
        };
        System.out.println(lambda2);
        return false;
      }
    });
  }

  static void bar(Unused unused) {
    unused.test();
  }
}
