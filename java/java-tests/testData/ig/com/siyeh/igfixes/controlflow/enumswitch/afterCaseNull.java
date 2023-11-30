// "Create missing branches: 'B', and 'D'" "true"
enum Foo { A, B, C, D, E }

class Test {
    void testI(Foo foo) {
        switch (foo) {
            case A -> System.out.println(1);
            case B -> {
            }
            case C -> System.out.println(2);
            case D -> {
            }
            case E -> System.out.println(3);
            case null -> System.out.println(4);
        }
    }
}