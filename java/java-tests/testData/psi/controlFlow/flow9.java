// LocalsOrMyInstanceFieldsControlFlowPolicy


import java.io.FileNotFoundException;
import java.io.FileReader;

class ExceptionTestCase {

    void cf1(int i) {<caret>
      Object o;
      try {
        o = new FileReader("");   //
      }
      catch (FileNotFoundException e) { //
      }
      finally {
        if (i==1) return;
      }
    }
}