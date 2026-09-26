import java.io.Serializable;

class Main {
  public static void main(String[] args) {
    var x = (Integer & Serializable) 10;
    System.out.println(x + 1);
  }
}