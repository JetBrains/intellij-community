class Main {
  void test(Object o) {
        switch (o) {
            case Long <caret>l when l != null ->
                System.out.println("Long: " + l);
            default ->
                System.out.println();
        }
    }
}