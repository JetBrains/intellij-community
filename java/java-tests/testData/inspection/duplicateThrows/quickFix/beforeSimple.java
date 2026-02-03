// "Remove 'IOException' from 'execute()' throws list" "true"
import java.io.*;

class X {

  void execute() throws IOE<caret>xception, IOException {

  }
}