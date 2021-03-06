import java.util.Collections;

public class MethodReference {
  public static void main(String[] args) {
    new Thread(Collections::emptyList);
  }
}