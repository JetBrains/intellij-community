import static java.lang.System.currentTimeMillis;

class X {

  public static void main(String[] args) {
    System.out.println("took " + (//simple end comment
            currentTimeMillis() - 1) + "ms");
  }

}