// "Remove 'IOException' from 'execute()' throws list" "false"
import java.io.*;

class X {

  void execute() throws XXX, IOException, YYY, IOEx<caret>ception {

  }
}