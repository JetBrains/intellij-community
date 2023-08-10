class C {
    String foo(int n) {
        return switch (n) {
            case 1 -> Integer.toString(n);
            case 2 -> { <warning descr="Labeled rule's code block is redundant">yield</warning> Integer.toString(n); }
            case 3 -> throw new RuntimeException();
            case 4 -> <warning descr="Labeled rule's code block is redundant">{</warning> throw new RuntimeException(); <warning descr="Labeled rule's code block is redundant">}</warning>
            case 5 -> { <warning descr="Labeled rule's code block is redundant">yield</warning> "a";}
            default -> "b";
        };
    }

    String nestedSwitchStatement(int n) {
      return switch (n) {
        case 1 -> {
          switch (n) {
            default -> { yield "2"; }
          }
        }
        default -> "b";
      };
    }

    String nestedSwitchExpression(int n) {
      return switch (n) {
        case 1 -> switch (n) {
          default -> { <warning descr="Labeled rule's code block is redundant">yield</warning> "2"; }
        };
        default -> "b";
      };
    }
}