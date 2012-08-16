class Test {
  protected <T> void applyChanges(final T variable) {
                 new Runnable() {
                     @Override
                     public void run() {
                         <selection>System.out.println(variable);</selection>
                     }
                 }
         ;
     }
}