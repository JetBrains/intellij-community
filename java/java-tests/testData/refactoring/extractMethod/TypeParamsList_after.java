class Test {
  protected <T extends java.util.List<K>, K> void applyChanges(final T variable) {
                 new Runnable() {
                     @Override
                     public void run() {
                         newMethod(variable);
                     }
                 }
         ;
     }

    private <T extends java.util.List<K>, K> void newMethod(T variable) {
        System.out.println(variable);
    }
}