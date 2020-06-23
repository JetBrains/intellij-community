package extractMethod;

import java.io.*;
import java.util.Iterator;

public class SCR27887 {
    public int publishx(OutputStream out, boolean includeCode) throws IOException {
        newMethod();
        while(true){
          OutputStream os = null;
          try {
          } finally {
            os.close();
          }
        }
    }

    private void newMethod() {
        ScatteringDocBuilder docBuilder = new MyDocBuilder(repository, included);
    }
}
