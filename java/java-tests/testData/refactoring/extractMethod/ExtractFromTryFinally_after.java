public class S {
  {
    String s;
    try {
        s = newMethod();
    } finally {
    }
    System.out.print(s);
  }

    private String newMethod() {
        String s;
        s = "";
        return s;
    }
}
