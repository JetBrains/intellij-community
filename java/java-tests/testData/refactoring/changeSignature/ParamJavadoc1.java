class X {

    /**
     * Has a method called {@link #mymethod(int)}.
     */
    public class TestRefactorLink {
      /**
       * @return nothing
       * @param y yparam
       */
        public void <caret>mymethod(int y) { }
    }
}
