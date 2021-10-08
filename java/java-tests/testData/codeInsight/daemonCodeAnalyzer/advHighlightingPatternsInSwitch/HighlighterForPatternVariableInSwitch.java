class Main {
  void test(Object o) {
        switch (o) {
            case ((Long <caret>l) && l != null) ->
                System.out.println("Long: " + l);
            default ->
                System.out.println();
        }
    }
}