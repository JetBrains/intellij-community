// "Remove explicit array creation" "true"
import java.util.Arrays;
import java.util.List;
import java.util.Date;

public class RedundantArrayForVarargsCall {
  public List<Date> severalValues() {
    return Arrays.asList(new <caret>Date[] {new Date(), new Date()});
  }
}