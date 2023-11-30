// "Merge with 'case E.A'" "false"
interface Something {

}

enum E implements Something {A, B,}

record P(String s) implements Something {
}

class Test {
  public static void main(String[] args) {

  }
  public int main1(Something something) {
    return switch (something) {
      case E.A: yield 1;
      case P(_) : yield<caret> 1;
      default: {
        yield 2;
      }
    };
  }
}