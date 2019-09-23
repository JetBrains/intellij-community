// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

import java.util.*;

class Test {

  String checkForNullable(String in) {
      if (in == null) throw new NullPointerException();
      String strOrNull = getStrOrNull(in);
      if (strOrNull == null) throw new NoSuchElementException("No value present");
      return strOrNull;
  }

  String checkIsRemovedForNotNull(String in) {
      if (in == null) throw new NullPointerException();
      String s = id(in);
      if (s.length() <= 2) throw new NoSuchElementException("No value present");
      return s;
  }

  String twoMapsProduceTwoVariables(String in, boolean b) {
      if (in == null) throw new NullPointerException();
      String s = id(in);
      String strIfTrue = getStrIfTrue(s, b);
      if (strIfTrue == null || strIfTrue.length <= 2) throw new NoSuchElementException("No value present");
      return strIfTrue;
  }

  private String id(String s) {
    return s;
  }

  private String getStrOrNull(String s) {
    return s.length() > 2 ? s : null;
  }

  private String getStrIfTrue(String s, boolean b) {
    return b ? s : null;
  }

}