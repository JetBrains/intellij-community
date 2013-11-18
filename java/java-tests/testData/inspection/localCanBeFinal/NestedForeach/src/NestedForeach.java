class NestedForeach {
  void foo(final List<String> list) {
    new Object() {
      void one() {
        new Object() {
          void two() {
            new Object() {
              void three() {
                for (String s : list) {
                  System.out.println(s);
                }
              }
            };
          }
        };
      }
    };
  }
}