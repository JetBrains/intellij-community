import java.util.*;

public class ArrayAccessWithCastInCountedLoop {
  void aioobe(int[] arr) {
    for (long i = 0; i < arr.length; i++) {
      System.out.println(arr[(int) i]);
    }
  }
}