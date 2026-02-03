// LocalsOrMyInstanceFieldsControlFlowPolicy



public class B {
    protected Object invokeNext(Object mi)
       throws Exception
    {<caret>
          try {
             return this;
          } finally {
             try {
             } finally {
                return null;
             }
          }
    }


}
