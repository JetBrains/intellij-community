// LocalsOrMyInstanceFieldsControlFlowPolicy

import java.io.IOException;

import java.io.EOFException;

public class a {
  
  int f(boolean b1, boolean b2) {<caret>        
  
  try {
            throw new IOException();
        } catch (EOFException eof) {
            eof.printStackTrace();
        } catch (IOException io) {
            io.printStackTrace();
        }

    return 0;
  }

}
