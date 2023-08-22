package x;

import java.io.DataInputStream;

public class RecursiveError implements <error descr="Interface expected here">DataInputStream</error> {

  @Override
  public boolean markSupported() {
      return super.<error descr="Cannot resolve method 'markSupported' in 'Object'">markSupported</error>();
  }
}
