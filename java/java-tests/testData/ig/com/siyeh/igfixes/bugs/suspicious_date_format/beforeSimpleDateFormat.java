// "Replace 'YYYY' with 'yyyy'" "true"
import java.text.*;

class X {
  SimpleDateFormat format = new SimpleDateFormat("YY<caret>YY/MM/dd");
}