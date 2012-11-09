class X {

    /**
     * Has a method called {@link #mymethod(boolean)}.
     */
    public class TestRefactorLink {
      /**
       * @param a aparam
       */
        public void <caret>mymethod(boolean a) { }
    }
}
