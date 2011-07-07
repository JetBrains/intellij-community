// LocalsOrMyInstanceFieldsControlFlowPolicy

class MyTest  {
    void f(Object o, long childrenStamp, Object o2) {<caret>
        long currentStamp;
        if ((o instanceof String && childrenStamp != (currentStamp = 0))
                ||
              (o2 instanceof Integer && childrenStamp != (currentStamp = 1))
           )
        {
          childrenStamp = currentStamp;
        }
    }

}
