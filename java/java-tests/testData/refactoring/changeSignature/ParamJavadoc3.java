class X {

    /**
     * Has a method called {@link #mymethod(boolean)}.
     */
    public class TestRefactorLink {
      /**
       * @param x aparam
       */
        public void <caret>mymethod(boolean a) { }
    }
}
