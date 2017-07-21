// "Replace with forEach" "false"
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

class Test {

  private List<byte[]> reqs;

  public ForEachTest () throws IOException {
    DataOutputStream req = new DataOutputStream(new ByteArrayOutputStream());
    for(byte[] val : r<caret>eqs) {
      req.write(val);
    }
  }
}
