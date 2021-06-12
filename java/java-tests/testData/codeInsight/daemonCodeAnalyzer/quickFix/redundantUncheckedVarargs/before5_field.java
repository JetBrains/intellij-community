// "Remove 'unchecked' suppression" "true"
import java.util.*;

public class Test {

  /**
   * unchecked in javadoc
   */
  @SuppressWarnings("unch<caret>ecked")
  List<ArrayList<String>> list = new ArrayList<String>(), list1 = null;
  
}

