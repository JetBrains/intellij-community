// "Remove explicit array creation" "true"
import java.util.Arrays;
import java.util.List;
import java.util.Date;

public class RedundantArrayForVarargsCall {
  {
    try {
      String.class.getConstructor(String.class);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}