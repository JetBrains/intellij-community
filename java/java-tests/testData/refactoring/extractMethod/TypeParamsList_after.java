class Test {
  protected <T> void applyChanges(final T variable) {
                 new Runnable() {
                     @Override
                     public void run() {
                         newMethod(variable);
                     }
                 }
         ;
     }

    private <T> void newMethod(T variable) {
        System.out.println(variable);
    }
}